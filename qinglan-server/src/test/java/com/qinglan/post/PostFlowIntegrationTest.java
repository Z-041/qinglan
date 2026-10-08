package com.qinglan.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1 发帖闭环的端到端验证（真实 MySQL）。
 *
 * <p>覆盖三条不变量：#1 匿名代号跨帖不可关联且不暴露 authorId、#2 联系方式服务端脱敏与查看留痕、
 * #5 先审后发不跳态。单元测试用的是 mock，这里跑的是真库真链路，能抓到映射与事务层面的问题。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PostFlowIntegrationTest {

    private static final String AUTHOR_EMAIL = "author@qinglan.edu.cn";
    private static final String AUTHOR_STUDENT_ID = "2023011001";
    private static final String READER_EMAIL = "reader@qinglan.edu.cn";
    private static final String READER_STUDENT_ID = "2023011002";
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

    @Test
    @DisplayName("闭环：发布落待审、他人看不到，管理员放行后才进信息流")
    void shouldPublishReviewThenAppearInFeed() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User reader = createStudent(READER_EMAIL, READER_STUDENT_ID);
        long postId = publish(author, CONTACT);

        // 待审：作者本人可见（详情里 mine = true），他人与游客一律按不存在处理
        mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", bearer(author, UserRole.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mine").value(true));
        mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", bearer(reader, UserRole.STUDENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-002"));
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isNotFound());

        // 学生无审核权
        mockMvc.perform(post("/api/admin/posts/{id}/approve", postId)
                        .header("Authorization", bearer(reader, UserRole.STUDENT)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/posts/{id}/approve", postId)
                        .header("Authorization", bearer(reader, UserRole.ADMIN)))
                .andExpect(status().isOk());

        // 放行后游客也能在信息流里看到，且只给匿名代号
        JsonNode items = readJson(mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("items");
        JsonNode target = itemWithId(items, postId);
        assertThat(target).isNotNull();
        assertThat(target.path("authorId").isMissingNode()).isTrue();
        assertThat(target.path("contact").isMissingNode()).isTrue();
        assertThat(target.path("anonymousCode").asText()).matches("[\\u4e00-\\u9fa5]{2}#[0-9a-f]{4}");
    }

    @Test
    @DisplayName("不变量 #2：游客拿不到联系方式，认证的非作者拿到且留下 CONTACT_VIEW 审计")
    void shouldMaskContactByViewerIdentity() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User reader = createStudent(READER_EMAIL, READER_STUDENT_ID);
        long postId = publish(author, CONTACT);
        approve(postId, reader);

        // 游客：contact 必须是服务端返回的 null，而不是靠前端隐藏
        JsonNode anonymous = detail(postId, null);
        assertThat(anonymous.path("contact").isNull()).isTrue();
        assertThat(anonymous.path("contactVisible").asBoolean()).isFalse();
        assertThat(auditLogCount(postId, "CONTACT_VIEW")).isZero();

        // 认证的非作者：可见，并留痕
        JsonNode authenticated = detail(postId, reader);
        assertThat(authenticated.path("contact").asText()).isEqualTo(CONTACT);
        assertThat(authenticated.path("contactVisible").asBoolean()).isTrue();
        assertThat(auditLogCount(postId, "CONTACT_VIEW")).isEqualTo(1);

        // 作者看自己的帖子不产生查看审计，避免日志被本人刷屏
        JsonNode own = detail(postId, author);
        assertThat(own.path("contact").asText()).isEqualTo(CONTACT);
        assertThat(auditLogCount(postId, "CONTACT_VIEW")).isEqualTo(1);
    }

    @Test
    @DisplayName("不变量 #1：同一作者在不同帖子下的代号不同")
    void shouldDeriveDistinctAnonymousCodePerPost() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User reviewer = createStudent(READER_EMAIL, READER_STUDENT_ID);

        long first = publish(author, CONTACT);
        long second = publish(author, CONTACT);
        approve(first, reviewer);
        approve(second, reviewer);

        String firstName = detail(first, null).path("anonymousCode").asText();
        String secondName = detail(second, null).path("anonymousCode").asText();

        assertThat(firstName).isNotEqualTo(secondName);
    }

    @Test
    @DisplayName("评论：两级结构生效，计数与可见评论数一致，点赞幂等")
    void shouldHandleCommentsAndLikesIdempotently() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User reader = createStudent(READER_EMAIL, READER_STUDENT_ID);
        long postId = publish(author, CONTACT);
        approve(postId, reader);
        // 已是完整的 Authorization 头取值（含 Bearer 前缀），不要再拼一次
        String readerHeader = bearer(reader, UserRole.STUDENT);

        long rootId = commentId(createComment(postId, readerHeader, "还在吗？", null));

        // 一级评论记为 1 条
        assertThat(detail(postId, null).path("commentCount").asInt()).isEqualTo(1);

        long replyId = commentId(createComment(postId, readerHeader, "已经出掉了", rootId));
        assertThat(detail(postId, null).path("commentCount").asInt()).isEqualTo(2);

        // 评论列表已改为一级评论游标分页：出参是 { items, nextCursor }
        JsonNode commentPage = readJson(mockMvc.perform(get("/api/posts/{id}/comments", postId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        JsonNode comments = commentPage.path("items");
        assertThat(comments).hasSize(1);
        assertThat(commentPage.path("nextCursor").isNull() || commentPage.path("nextCursor").asText().isEmpty())
                .isTrue();
        assertThat(comments.get(0).path("replies")).hasSize(1);
        assertThat(comments.get(0).path("replies").get(0).path("authorId").isMissingNode()).isTrue();

        // 三级嵌套被拒绝
        mockMvc.perform(post("/api/posts/{id}/comments", postId)
                        .header("Authorization", readerHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "三级回复", "parentId", replyId))))
                .andExpect(status().isNotFound());

        // 点赞幂等：重复 POST 只落一条记录
        mockMvc.perform(post("/api/posts/{id}/like", postId).header("Authorization", readerHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(post("/api/posts/{id}/like", postId).header("Authorization", readerHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));

        Integer likeRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM like_record WHERE target_type = 'POST' AND target_id = ?",
                Integer.class, postId);
        assertThat(likeRows).isEqualTo(1);
    }

    @Test
    @DisplayName("R-15：删除评论后其点赞行同一事务内清理，真库里不留孤儿数据")
    void shouldPurgeCommentLikesOnDelete() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User reader = createStudent(READER_EMAIL, READER_STUDENT_ID);
        long postId = publish(author, CONTACT);
        approve(postId, reader);
        String readerHeader = bearer(reader, UserRole.STUDENT);
        String authorHeader = bearer(author, UserRole.STUDENT);

        long rootId = commentId(createComment(postId, readerHeader, "还在吗？", null));
        long replyId = commentId(createComment(postId, readerHeader, "已经出掉了", rootId));

        mockMvc.perform(post("/api/comments/{id}/like", rootId).header("Authorization", authorHeader))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/comments/{id}/like", replyId).header("Authorization", authorHeader))
                .andExpect(status().isOk());
        assertThat(commentLikeRows(rootId)).isEqualTo(1);
        assertThat(commentLikeRows(replyId)).isEqualTo(1);

        // 删一级评论会连带移除其回复，两条的点赞行都必须同事务消失
        mockMvc.perform(delete("/api/comments/{id}", rootId).header("Authorization", readerHeader))
                .andExpect(status().isOk());

        assertThat(commentLikeRows(rootId)).isZero();
        assertThat(commentLikeRows(replyId)).isZero();
    }

    @Test
    @DisplayName("B：封禁即时生效——旧令牌的评论与点赞一律被拒（无状态令牌不会自动失效）")
    void shouldEnforceBanOnWritePathsImmediately() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User reader = createStudent(READER_EMAIL, READER_STUDENT_ID);
        long postId = publish(author, CONTACT);
        approve(postId, reader);
        // 令牌先签发，随后才封禁：这正是「持旧令牌继续写」的现场
        String readerHeader = bearer(reader, UserRole.STUDENT);
        userService.ban(reader.getId(), Duration.ofDays(3), "测试封禁", author.getId());

        mockMvc.perform(post("/api/posts/{id}/comments", postId)
                        .header("Authorization", readerHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "还能发吗"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-002"));

        mockMvc.perform(post("/api/posts/{id}/like", postId).header("Authorization", readerHeader))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-002"));

        // 只读不受影响：封禁用户仍可浏览
        mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", readerHeader))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 辅助

    private long publish(User author, String contact) throws Exception {
        long topicId = topicId("二手");
        String body = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(author, UserRole.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreatePostRequest(topicId, "出一辆九成新自行车", contact, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anonymousCode").isNotEmpty())
                .andExpect(jsonPath("$.data.authorId").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body).path("data").path("id").asLong();
    }

    private void approve(long postId, User operator) throws Exception {
        mockMvc.perform(post("/api/admin/posts/{id}/approve", postId)
                        .header("Authorization", bearer(operator, UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    private JsonNode detail(long postId, User viewer) throws Exception {
        var request = get("/api/posts/{id}", postId);
        if (viewer != null) {
            request.header("Authorization", bearer(viewer, UserRole.STUDENT));
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body).path("data");
    }

    private String createComment(long postId, String bearerToken, String content, Long parentId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("parentId", parentId);
        return mockMvc.perform(post("/api/posts/{id}/comments", postId)
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long commentId(String body) throws Exception {
        return readJson(body).path("data").path("id").asLong();
    }

    private int commentLikeRows(long commentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM like_record WHERE target_type = 'COMMENT' AND target_id = ?",
                Integer.class, commentId);
        return count == null ? 0 : count;
    }

    private int auditLogCount(long postId, String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_log WHERE target_type = 'POST' AND target_id = ? AND action = ?",
                Integer.class, postId, action);
        return count == null ? 0 : count;
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
     * 早先拿到的 user 引用可能已经脱离持久化上下文，直接改它会**静默不落库**。</p>
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

    private static JsonNode itemWithId(JsonNode items, long id) {
        for (JsonNode item : items) {
            if (item.path("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }
}
