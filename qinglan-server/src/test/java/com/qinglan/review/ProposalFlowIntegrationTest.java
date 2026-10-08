package com.qinglan.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 表决机制的端到端验证（P5 二期，真实 MySQL）。
 *
 * <p>覆盖设计 10.2 步 3 的通过标准：发起提案 → 委员会投票 → 达阈值即在同一事务内执行处置，
 * 且处置 operator 为系统哨兵、理由带提案号（决策 D10）。同时验证「同目标仅一个进行中提案」
 * 与「一人一票」两条唯一约束都映射成 409。</p>
 *
 * <p>造数令数据库角色与令牌一致（P5 D9 的前提）：先授予角色、再签发令牌。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProposalFlowIntegrationTest {

    private static final String CONTACT = "微信 qinglan_demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("发起下架提案：委员会达阈值即自动下架，审计含提案号且 operator 为系统哨兵")
    void shouldExecuteTakeDownWhenThresholdReached() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User admin1 = createStudent("a1@qinglan.edu.cn", "2023012001");
        User admin2 = createStudent("a2@qinglan.edu.cn", "2023012002");
        User admin3 = createStudent("a3@qinglan.edu.cn", "2023012003");

        long postId = publish(author);
        approve(postId, admin1);
        // 委员会在册 3 人：提案创建时的分母快照即 3，needed = 3/2 + 1 = 2
        String token1 = bearer(admin1, UserRole.ADMIN);
        String token2 = bearer(admin2, UserRole.ADMIN);
        bearer(admin3, UserRole.ADMIN);

        long proposalId = createProposal(token1, postId, "ABUSE", "人身攻击");
        assertThat(proposalAuditCount(postId, "PROPOSAL_CREATE")).isEqualTo(1);

        // 第 1 票同意：1 < 2 但仍可能达标 → 保持 OPEN，帖子仍可见
        vote(token1, proposalId, "AGREE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.needed").value(2));
        assertThat(postStatus(postId)).isEqualTo("APPROVED");

        // 第 2 票同意：达到阈值 → 立即执行处置
        vote(token2, proposalId, "AGREE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.agreeCount").value(2));

        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        // operator 为系统哨兵 0，不会把任何管理员钉成「原处置人」（方案 5.6）
        assertThat(auditCount(postId, "POST_TAKEDOWN", 0L)).isEqualTo(1);
        assertThat(takedownReason(postId)).isEqualTo("ABUSE: 人身攻击");
        // 提案结案也留痕，理由带提案号
        assertThat(resolveReason(postId)).startsWith("表决通过（提案 #" + proposalId);
        assertThat(proposalStatus(proposalId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("同一目标同时只允许一个进行中提案：第二个返回 409")
    void shouldRejectSecondOpenProposalOnSameTarget() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User admin = createStudent("admin@qinglan.edu.cn", "2023012001");

        long postId = publish(author);
        approve(postId, admin);
        String token = bearer(admin, UserRole.ADMIN);

        createProposal(token, postId, "ABUSE", null);

        mockMvc.perform(post("/api/admin/proposals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposalBody(postId, "ABUSE", "重复提案")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-010"));
    }

    @Test
    @DisplayName("一人一票：同一人重复投票返回 409，且不静默吞掉")
    void shouldRejectDuplicateVote() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User admin1 = createStudent("a1@qinglan.edu.cn", "2023012001");
        User admin2 = createStudent("a2@qinglan.edu.cn", "2023012002");

        long postId = publish(author);
        approve(postId, admin1);
        // 在册 2 人：1 票同意既未达标也未到不可能，提案保持 OPEN，才测得到「重复投票」
        String token1 = bearer(admin1, UserRole.ADMIN);
        bearer(admin2, UserRole.ADMIN);

        long proposalId = createProposal(token1, postId, "ABUSE", null);
        vote(token1, proposalId, "AGREE").andExpect(status().isOk());

        vote(token1, proposalId, "DISAGREE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-011"));
    }

    @Test
    @DisplayName("提案列表只给汇总，不含发起人与投票人身份（方案 D8）")
    void shouldListSummaryWithoutIdentity() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User admin1 = createStudent("a1@qinglan.edu.cn", "2023012001");
        User admin2 = createStudent("a2@qinglan.edu.cn", "2023012002");

        long postId = publish(author);
        approve(postId, admin1);
        // 在册 2 人：1 票同意后仍 OPEN，列表才能取到这条进行中的提案
        String token1 = bearer(admin1, UserRole.ADMIN);
        bearer(admin2, UserRole.ADMIN);
        long proposalId = createProposal(token1, postId, "ABUSE", "人身攻击");
        vote(token1, proposalId, "AGREE").andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/proposals").header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].proposalId").value(proposalId))
                .andExpect(jsonPath("$.data.items[0].agreeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].needed").value(2))
                .andExpect(jsonPath("$.data.items[0].myDecision").value("AGREE"))
                .andExpect(jsonPath("$.data.items[0].preview").value("出一辆九成新自行车"))
                .andExpect(jsonPath("$.data.items[0].proposerId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].votes").doesNotExist());
    }

    // ------------------------------------------------------------------ 僵局与超管裁决

    @Test
    @DisplayName("僵局上报 → 超管裁决不予处置：提案 REJECTED，内容保持可见")
    void shouldEscalateThenDenyBySuperAdmin() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User admin1 = createStudent("a1@qinglan.edu.cn", "2023012001");
        User admin2 = createStudent("a2@qinglan.edu.cn", "2023012002");
        User superAdmin = createStudent("root@qinglan.edu.cn", "2023019001");

        long postId = publish(author);
        approve(postId, admin1);
        String token1 = bearer(admin1, UserRole.ADMIN);
        String token2 = bearer(admin2, UserRole.ADMIN);
        String rootToken = bearer(superAdmin, UserRole.SUPER_ADMIN);

        long proposalId = createProposal(token1, postId, "ABUSE", "人身攻击");
        // 在册 2 人、需 2 票：1 同意 1 不同意后，未投票者 0 人，最多凑到 1 票 → 不可能过半
        vote(token1, proposalId, "AGREE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
        vote(token2, proposalId, "DISAGREE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ESCALATED"));

        // 待裁决列表：仅超管可见，且带每一票明细（决策 D8）
        mockMvc.perform(get("/api/admin/proposals/escalated").header("Authorization", rootToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].proposalId").value(proposalId))
                .andExpect(jsonPath("$.data.items[0].status").value("ESCALATED"))
                .andExpect(jsonPath("$.data.items[0].votes.length()").value(2));

        mockMvc.perform(post("/api/admin/proposals/{id}/arbitrate", proposalId)
                        .header("Authorization", rootToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "DENY", "reason", "证据不足"))))
                .andExpect(status().isOk());

        assertThat(proposalStatus(proposalId)).isEqualTo("REJECTED");
        assertThat(postStatus(postId)).isEqualTo("APPROVED");
        // 裁决留痕：operator 是超管本人（与表决执行的系统哨兵相区分）
        assertThat(auditCount(postId, "PROPOSAL_RESOLVE", superAdmin.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("僵局上报 → 超管裁决准予处置：执行下架，处置仍以系统哨兵留痕")
    void shouldEscalateThenGrantBySuperAdmin() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User admin1 = createStudent("a1@qinglan.edu.cn", "2023012001");
        User admin2 = createStudent("a2@qinglan.edu.cn", "2023012002");
        User superAdmin = createStudent("root@qinglan.edu.cn", "2023019001");

        long postId = publish(author);
        approve(postId, admin1);
        String token1 = bearer(admin1, UserRole.ADMIN);
        String token2 = bearer(admin2, UserRole.ADMIN);
        String rootToken = bearer(superAdmin, UserRole.SUPER_ADMIN);

        long proposalId = createProposal(token1, postId, "ABUSE", "人身攻击");
        vote(token1, proposalId, "AGREE").andExpect(status().isOk());
        vote(token2, proposalId, "DISAGREE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ESCALATED"));

        mockMvc.perform(post("/api/admin/proposals/{id}/arbitrate", proposalId)
                        .header("Authorization", rootToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "GRANT", "reason", "确认违规"))))
                .andExpect(status().isOk());

        assertThat(proposalStatus(proposalId)).isEqualTo("APPROVED");
        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        // 处置 operator 仍是系统哨兵（D10）：不把超管钉成「原处置人」
        assertThat(auditCount(postId, "POST_TAKEDOWN", 0L)).isEqualTo(1);
        assertThat(auditCount(postId, "PROPOSAL_RESOLVE", superAdmin.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 辅助

    private long publish(User author) throws Exception {
        long topicId = topicId("二手");
        String body = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(author, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreatePostRequest(topicId, "出一辆九成新自行车", CONTACT, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body).path("data").path("id").asLong();
    }

    private void approve(long postId, User operator) throws Exception {
        mockMvc.perform(post("/api/admin/posts/{id}/approve", postId)
                        .header("Authorization", bearer(operator, UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    private long createProposal(String token, long postId, String reasonCode, String detail) throws Exception {
        String body = mockMvc.perform(post("/api/admin/proposals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposalBody(postId, reasonCode, detail)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body).path("data").path("proposalId").asLong();
    }

    private String proposalBody(long postId, String reasonCode, String detail) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("targetType", "POST");
        payload.put("targetId", postId);
        payload.put("action", "TAKE_DOWN");
        payload.put("reasonCode", reasonCode);
        payload.put("detail", detail);
        return json(payload);
    }

    private org.springframework.test.web.servlet.ResultActions vote(
            String token, long proposalId, String decision) throws Exception {
        return mockMvc.perform(post("/api/admin/proposals/{id}/vote", proposalId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("decision", decision))));
    }

    private String postStatus(long postId) {
        flushPending();
        return jdbcTemplate.queryForObject("SELECT status FROM post WHERE id = ?", String.class, postId);
    }

    private String proposalStatus(long proposalId) {
        flushPending();
        return jdbcTemplate.queryForObject(
                "SELECT status FROM review_proposal WHERE id = ?", String.class, proposalId);
    }

    private String takedownReason(long postId) {
        flushPending();
        return jdbcTemplate.queryForObject(
                "SELECT reason FROM review_log WHERE target_type = 'POST' AND target_id = ? "
                        + "AND action = 'POST_TAKEDOWN'", String.class, postId);
    }

    private String resolveReason(long postId) {
        flushPending();
        return jdbcTemplate.queryForObject(
                "SELECT reason FROM review_log WHERE target_type = 'POST' AND target_id = ? "
                        + "AND action = 'PROPOSAL_RESOLVE'", String.class, postId);
    }

    private int auditCount(long postId, String action, long operatorId) {
        flushPending();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_log WHERE target_type = 'POST' AND target_id = ? "
                        + "AND action = ? AND operator_id = ?",
                Integer.class, postId, action, operatorId);
        return count == null ? 0 : count;
    }

    private int proposalAuditCount(long postId, String action) {
        flushPending();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_log WHERE target_type = 'POST' AND target_id = ? AND action = ?",
                Integer.class, postId, action);
        return count == null ? 0 : count;
    }

    /**
     * JPA 把 flush 推迟到事务提交，而 {@link JdbcTemplate} 完全绕过持久化上下文、不读一级缓存，
     * 因此查库前必须显式 flush，否则读到的是事务开始时的旧值。
     */
    private void flushPending() {
        entityManager.flush();
    }

    private long topicId(String name) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM topic WHERE name = ?", Long.class, name);
        assertThat(id).as("预设话题 %s 应由 Flyway 种入", name).isNotNull();
        return id;
    }

    private User createStudent(String email, String studentId) {
        return userService.createVerifiedStudent(cryptoService.hashEmail(email), studentId);
    }

    /**
     * 发令牌，并把数据库里的角色一并对齐（P5 D9：写入口会比对令牌角色与数据库角色）。
     * 用 {@code requireById} 重新取一次再改，避免早先引用脱离持久化上下文后改而不落库。
     */
    private String bearer(User user, UserRole role) {
        userService.requireById(user.getId()).changeRole(role);
        return "Bearer " + jwtService.issueAccessToken(user.getId(), role);
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
