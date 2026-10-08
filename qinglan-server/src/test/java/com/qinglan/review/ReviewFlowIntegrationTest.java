package com.qinglan.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.review.dto.CreateReportRequest;
import com.qinglan.review.dto.ProposalDispositionRequest;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.service.ReviewService;
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
 * P2 审核闭环的端到端验证（真实 MySQL）。
 *
 * <p>覆盖：#4 举报不可刷量、举报数达阈值自动降级、队列不含举报人身份（不变量 #1 引申）、
 * #7 处置全程留痕、以及「复审人 ≠ 原处置人」这条申诉硬规则。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewFlowIntegrationTest {

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
    private ReviewService reviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("闭环：举报达阈值自动下架 → 队列可见次日 → 作者申诉 → 复审通过恢复可见")
    void shouldAutoTakeDownThenRestoreViaAppeal() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User r1 = createStudent("r1@qinglan.edu.cn", "2023011101");
        User r2 = createStudent("r2@qinglan.edu.cn", "2023011102");
        User r3 = createStudent("r3@qinglan.edu.cn", "2023011103");
        User admin = createStudent("admin@qinglan.edu.cn", "2023019999");

        long postId = publish(author);
        approve(postId, admin);

        report(r1, postId, ReportReason.ADVERTISEMENT);
        report(r2, postId, ReportReason.ABUSE);
        assertThat(postStatus(postId)).isEqualTo("APPROVED");

        // 不变量 #4：同一人重复举报被拒，不静默吞掉（此时帖子仍可见，才会走到重复判定）
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", bearer(r1, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateReportRequest(
                                ReviewTargetType.POST, postId, ReportReason.ABUSE, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-005"));

        report(r3, postId, ReportReason.PRIVACY);

        // 第 3 条举报触发自动下架：他人按不存在处理，作者看到原因
        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        mockMvc.perform(get("/api/posts/{id}", postId)
                        .header("Authorization", bearer(r1, UserRole.STUDENT)))
                .andExpect(status().isNotFound());
        JsonNode own = detail(postId, author);
        assertThat(own.path("reviewReason").asText()).isEqualTo("AUTO_REPORT_THRESHOLD");
        assertThat(own.path("takenDown").asBoolean()).as("作者据此拿到申诉入口").isTrue();
        assertThat(auditCount(postId, "POST_AUTO_TAKEDOWN")).isEqualTo(1);

        // 队列：目标聚合为一行，且不出现任何举报人身份字段
        JsonNode queueItem = queueItem(admin, postId);
        assertThat(queueItem.path("reportCount").asInt()).isEqualTo(3);
        assertThat(queueItem.path("targetStatus").asText()).isEqualTo("TAKEN_DOWN");
        assertThat(queueItem.path("reporterId").isMissingNode()).isTrue();
        assertThat(queueItem.path("reporter").isMissingNode()).isTrue();
        assertThat(queueItem.path("reasons")).hasSize(3);

        // 作者申诉，且每帖限一次
        submitAppeal(author, postId, "内容并未违规");
        mockMvc.perform(post("/api/appeals")
                        .header("Authorization", bearer(author, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("postId", postId, "reason", "再申诉一次"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-006"));

        // 复审通过：内容恢复可见，全程留痕
        long appealId = appealId(postId);
        mockMvc.perform(post("/api/admin/appeals/{id}/accept", appealId)
                        .header("Authorization", bearer(admin, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "确系误判"))))
                .andExpect(status().isOk());

        assertThat(postStatus(postId)).isEqualTo("APPROVED");
        assertThat(appealStatus(appealId)).isEqualTo("ACCEPTED");
        JsonNode afterRestore = detail(postId, author);
        assertThat(afterRestore.path("takenDown").asBoolean()).as("恢复后申诉入口应收起").isFalse();
        assertThat(afterRestore.path("reviewReason").isNull()).as("下架理由随恢复清空").isTrue();
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isOk());
        assertThat(auditCount(postId, "APPEAL_REVIEW")).isEqualTo(1);
        assertThat(auditCount(postId, "POST_RESTORE")).isEqualTo(1);
    }

    @Test
    @DisplayName("复审人不得是原处置人：原处置人复审被拒，换人后通过并恢复")
    void shouldForbidSelfReviewOfOwnTakeDown() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User reporter = createStudent("reporter@qinglan.edu.cn", "2023011002");
        User operator = createStudent("operator@qinglan.edu.cn", "2023012001");
        User reviewer = createStudent("reviewer@qinglan.edu.cn", "2023012002");

        long postId = publish(author);
        approve(postId, operator);
        report(reporter, postId, ReportReason.ABUSE);
        takeDown(operator, postId, ReportReason.ABUSE, "人身攻击");
        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");

        submitAppeal(author, postId, "并没有攻击任何人");
        long appealId = appealId(postId);

        // 原处置人不能复核自己的裁决
        mockMvc.perform(post("/api/admin/appeals/{id}/accept", appealId)
                        .header("Authorization", bearer(operator, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "我自己改判"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-005"));
        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        assertThat(appealStatus(appealId)).isEqualTo("PENDING");

        // 换一位管理员复审才放行
        mockMvc.perform(post("/api/admin/appeals/{id}/accept", appealId)
                        .header("Authorization", bearer(reviewer, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "确系误判"))))
                .andExpect(status().isOk());

        assertThat(postStatus(postId)).isEqualTo("APPROVED");
        assertThat(appealStatus(appealId)).isEqualTo("ACCEPTED");
        assertThat(auditCount(postId, "APPEAL_REVIEW")).isEqualTo(1);
    }

    @Test
    @DisplayName("复审驳回：维持下架，内容不恢复")
    void shouldKeepTakenDownWhenAppealRejected() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User operator = createStudent("operator@qinglan.edu.cn", "2023012001");
        User reviewer = createStudent("reviewer@qinglan.edu.cn", "2023012002");

        long postId = publish(author);
        approve(postId, operator);
        takeDown(operator, postId, ReportReason.PORNOGRAPHY, null);

        submitAppeal(author, postId, "只是玩笑");
        long appealId = appealId(postId);

        mockMvc.perform(post("/api/admin/appeals/{id}/reject", appealId)
                        .header("Authorization", bearer(reviewer, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "维持原判"))))
                .andExpect(status().isOk());

        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        assertThat(appealStatus(appealId)).isEqualTo("REJECTED");
        assertThat(auditCount(postId, "APPEAL_REVIEW")).isEqualTo(1);
        assertThat(auditCount(postId, "POST_RESTORE")).isZero();
    }

    @Test
    @DisplayName("非作者不能替他人申诉，且未下架的帖子无从申诉")
    void shouldRejectAppealFromNonAuthorOrApprovedPost() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User other = createStudent("other@qinglan.edu.cn", "2023011002");
        User admin = createStudent("admin@qinglan.edu.cn", "2023019999");

        long postId = publish(author);
        approve(postId, admin);

        // 未下架：没有可申诉的处置
        mockMvc.perform(post("/api/appeals")
                        .header("Authorization", bearer(author, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("postId", postId, "reason", "我要申诉"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-004"));

        takeDown(admin, postId, ReportReason.OTHER, null);

        // 下架后：非作者申诉一律拒绝
        mockMvc.perform(post("/api/appeals")
                        .header("Authorization", bearer(other, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("postId", postId, "reason", "不关我的事"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-003"));
    }

    // ------------------------------------------------------------------ 自动降级的人工确认 / 复审幂等

    @Test
    @DisplayName("自动降级后的人工确认：维持下架把待处理举报结案为 UPHELD、计数清零，内容仍不可见")
    void shouldUpholdReportsOfAutoTakenDownPost() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User r1 = createStudent("r1@qinglan.edu.cn", "2023011101");
        User r2 = createStudent("r2@qinglan.edu.cn", "2023011102");
        User r3 = createStudent("r3@qinglan.edu.cn", "2023011103");
        User admin = createStudent("admin@qinglan.edu.cn", "2023019999");

        long postId = publish(author);
        approve(postId, admin);
        report(r1, postId, ReportReason.ADVERTISEMENT);
        report(r2, postId, ReportReason.ABUSE);
        report(r3, postId, ReportReason.PRIVACY);
        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        assertThat(reportCount(postId)).isEqualTo(3);

        // 二期起 HTTP 的 uphold-reports 也走表决；本用例验证的是「维持下架把举报结案」的处置本身
        reviewService.upholdReports(postId, admin.getId(), "确认违规，维持下架");

        // 内容仍在架下（维持下架不做状态迁移），举报结案为「成立」，待处理计数清零
        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        assertThat(reportCount(postId)).isZero();
        assertThat(reportCountByStatus(postId, "UPHELD")).isEqualTo(3);
        assertThat(auditCount(postId, "REPORT_UPHOLD")).isEqualTo(1);
    }

    @Test
    @DisplayName("复审前帖子已被超管手工恢复：复审通过不再因状态机冲突整体回滚")
    void shouldAcceptAppealWhenPostAlreadyRestoredManually() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User operator = createStudent("operator@qinglan.edu.cn", "2023012001");
        User restorer = createStudent("restorer@qinglan.edu.cn", "2023012002");
        User reviewer = createStudent("reviewer@qinglan.edu.cn", "2023012003");

        long postId = publish(author);
        approve(postId, operator);
        takeDown(operator, postId, ReportReason.ABUSE, "人身攻击");
        submitAppeal(author, postId, "并没有攻击任何人");

        // 复审尚未发生，超管先把帖子手工恢复（撤销处置需要 DISPOSITION_REVOKE，仅超管）
        restore(restorer, UserRole.SUPER_ADMIN, postId, "误判，先行恢复");
        assertThat(postStatus(postId)).isEqualTo("APPROVED");

        long appealId = appealId(postId);
        mockMvc.perform(post("/api/admin/appeals/{id}/accept", appealId)
                        .header("Authorization", bearer(reviewer, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "确系误判"))))
                .andExpect(status().isOk());

        // 申诉正常结案，不因为「已是 Approved」而整体回滚；内容本就可见，恢复这一步幂等跳过
        assertThat(appealStatus(appealId)).isEqualTo("ACCEPTED");
        assertThat(postStatus(postId)).isEqualTo("APPROVED");
        assertThat(auditCount(postId, "APPEAL_REVIEW")).isEqualTo(1);
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

    private void report(User reporter, long postId, ReportReason reason) throws Exception {
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", bearer(reporter, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateReportRequest(ReviewTargetType.POST, postId, reason, null))))
                .andExpect(status().isOk());
    }

    /**
     * 直接下架造数（不走 HTTP）。
     *
     * <p>二期起 HTTP 的 take-down 会走「发起提案 + 委员会表决」（见 {@code ProposalFlowIntegrationTest}），
     * 而表决执行时 {@code review_by} 记的是系统哨兵——本类要验证的申诉链路却需要「帖子已下架且记着
     * 某个具体处置人」（复审独立性判定读的就是 {@code post.review_by}），故用服务层直接处置造数。</p>
     */
    private void takeDown(User operator, long postId, ReportReason reason, String detail) {
        reviewService.takeDown(postId, operator.getId(), new ProposalDispositionRequest(reason, detail));
    }

    /**
     * 手工恢复可见（撤销处置）。撤销权归超管（{@code DISPOSITION_REVOKE}），
     * 因此调用方必须传 SUPER_ADMIN 令牌，否则会被 403。
     */
    private void restore(User operator, UserRole role, long postId, String reason) throws Exception {
        mockMvc.perform(post("/api/admin/posts/{id}/restore", postId)
                        .header("Authorization", bearer(operator, role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", reason))))
                .andExpect(status().isOk());
    }

    private void submitAppeal(User appellant, long postId, String reason) throws Exception {
        mockMvc.perform(post("/api/appeals")
                        .header("Authorization", bearer(appellant, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("postId", postId, "reason", reason))))
                .andExpect(status().isOk());
    }

    private JsonNode queueItem(User admin, long postId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/review-queue")
                        .header("Authorization", bearer(admin, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode item : readJson(body).path("data").path("items")) {
            if (item.path("targetId").asLong() == postId) {
                return item;
            }
        }
        throw new AssertionError("队列里没有帖子 " + postId);
    }

    private JsonNode detail(long postId, User viewer) throws Exception {
        String body = mockMvc.perform(get("/api/posts/{id}", postId)
                        .header("Authorization", bearer(viewer, UserRole.STUDENT)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body).path("data");
    }

    private String postStatus(long postId) {
        flushPending();
        return jdbcTemplate.queryForObject("SELECT status FROM post WHERE id = ?", String.class, postId);
    }

    private long appealId(long postId) {
        flushPending();
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM appeal WHERE target_type = 'POST' AND target_id = ?", Long.class, postId);
        assertThat(id).isNotNull();
        return id;
    }

    private String appealStatus(long appealId) {
        flushPending();
        return jdbcTemplate.queryForObject("SELECT status FROM appeal WHERE id = ?", String.class, appealId);
    }

    private int auditCount(long postId, String action) {
        flushPending();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_log WHERE target_type = 'POST' AND target_id = ? AND action = ?",
                Integer.class, postId, action);
        return count == null ? 0 : count;
    }

    private int reportCount(long postId) {
        flushPending();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT report_count FROM post WHERE id = ?", Integer.class, postId);
        return count == null ? 0 : count;
    }

    private int reportCountByStatus(long postId, String status) {
        flushPending();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE target_type = 'POST' AND target_id = ? AND status = ?",
                Integer.class, postId, status);
        return count == null ? 0 : count;
    }

    /**
     * JPA 把 flush 推迟到事务提交，而 {@link JdbcTemplate} 完全绕过持久化上下文，不读一级缓存。
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
     * 发令牌，并把数据库里的角色一并对齐。
     *
     * <p>P5 之后管理端写入口会比对「令牌角色 = 数据库角色」（决策 D9），只改令牌不改库会被当场拒绝。
     * 对齐也符合真实流程：先授予权限，再签发令牌。</p>
     *
     * <p>用 {@code requireById} 重新取一次再改：测试中途调用过 {@code entityManager.clear()}，
     * 早先拿到的 user 引用可能已经脱离持久化上下文，直接改它会**静默不落库**，
     * 表现为「令牌是 ADMIN、库里还是 STUDENT」而被写入口 403。</p>
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
