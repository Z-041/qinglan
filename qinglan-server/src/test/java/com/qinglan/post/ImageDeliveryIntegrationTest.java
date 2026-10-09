package com.qinglan.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.repository.PostRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-27① 的核心验收：受控图片下发端点的可见性、缓存头与路径安全（真实 MySQL + 本地存储实现）。
 *
 * <p>可见性口径复用 {@code Post.canBeViewedBy(viewerId)}：已通过人人可见，待审 / 驳回 / 下架
 * 仅作者可见；未被帖子引用的上传仅上传者可见。可见性随状态**即时**变化——下架后游客立即 404，
 * 且作者仍能取到（对象不删，申诉可恢复，不变量 #5）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageDeliveryIntegrationTest {

    private static final String AUTHOR_EMAIL = "img-author@qinglan.edu.cn";
    private static final String AUTHOR_STUDENT_ID = "2023012001";
    private static final String OTHER_EMAIL = "img-other@qinglan.edu.cn";
    private static final String OTHER_STUDENT_ID = "2023012002";
    /** 19 字节 JPEG 文件头，满足 ImageType.detect 的最小长度 */
    private static final byte[] JPEG =
            HexFormat.of().parseHex("ffd8ffe000104a46494600010100000100010000");

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
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("APPROVED 图游客 200；PENDING 图游客 404 而作者 200；TAKEN_DOWN 图游客立即 404 而作者 200")
    void shouldEnforcePostVisibilityOnImages() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User other = createStudent(OTHER_EMAIL, OTHER_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);
        String otherHeader = bearer(other, UserRole.STUDENT);

        String url = upload(authorHeader);
        long postId = publish(authorHeader, url);

        // 待审：作者 200，且带正确的 Content-Type、缓存头与校验器
        mockMvc.perform(get(url).header("Authorization", authorHeader))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/jpeg")))
                .andExpect(header().exists("Cache-Control"))
                .andExpect(header().exists("ETag"))
                .andExpect(header().exists("Last-Modified"));

        // 待审：游客与他人一律 404（不泄露存在性）
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get(url).header("Authorization", otherHeader)).andExpect(status().isNotFound());

        // 放行后：游客也能取到
        approve(postId);
        mockMvc.perform(get(url)).andExpect(status().isOk());

        // 下架后：游客立即 404，作者仍 200（对象不删，申诉可恢复）
        takeDown(postId);
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get(url).header("Authorization", authorHeader)).andExpect(status().isOk());

        // 申诉通过恢复可见后：游客重新可见
        restore(postId);
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("未被任何帖子引用的上传仅上传者可见；不存在/穿越/非法形态一律 404")
    void shouldHandleUnboundAndInvalid() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);

        String url = upload(authorHeader);
        mockMvc.perform(get(url).header("Authorization", authorHeader)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());

        // 不存在的对象：404 且用统一的图片错误码（与「不可见」不可区分）
        String missing = "/uploads/2026/10/" + "f".repeat(32) + ".jpg";
        mockMvc.perform(get(missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-010"));

        // 形态不合规（能到达端点）→ 404，不泄露对象是否存在
        mockMvc.perform(get("/uploads/2026/10/not-a-uuid.jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/2026/10/" + "A".repeat(32) + ".jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/2026/10/" + "a".repeat(32) + ".gif")).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/2026/10/" + "a".repeat(32))).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/other/10/" + "a".repeat(32) + ".jpg")).andExpect(status().isNotFound());

        // 含 ".." 的穿越尝试被 Spring Security 的 StrictHttpFirewall **更早**拒绝（400），根本到不了端点；
        // 与端点的 404 一样都不泄露存在性，故这里只断言「被拒」。
        mockMvc.perform(get("/uploads/2026/10/../../secret.jpg")).andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ 辅助

    private String upload(String authorHeader) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG);
        String body = mockMvc.perform(multipart("/api/uploads/images")
                        .file(file)
                        .header("Authorization", authorHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String url = readJson(body).path("data").path("url").asText();
        // 上传接口已改为返回签名 URL（私有图即刻可预览，见 T-30）。本用例专测「按 viewer 身份判可见」，
        // 故取干净 URL（去掉签名查询串）走身份路径——签名路径的验收在 ImageSignedUrlIntegrationTest。
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    private long publish(String authorHeader, String imageUrl) throws Exception {
        long topicId = topicId("问答");
        String body = mockMvc.perform(post("/api/posts")
                        .header("Authorization", authorHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePostRequest(topicId, "带图的帖子", null, List.of(imageUrl)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body).path("data").path("id").asLong();
    }

    /** 审核状态迁移直接用状态机（本用例只关心图片可见性，不重复验证管理端权限链路） */
    private void approve(long postId) {
        transition(postId, new ReviewState.Reviewing(1L, Instant.now()));
        transition(postId, new ReviewState.Approved(1L, Instant.now()));
    }

    private void takeDown(long postId) {
        transition(postId, new ReviewState.TakenDown(1L, "违规内容", Instant.now()));
    }

    private void restore(long postId) {
        transition(postId, new ReviewState.Approved(1L, Instant.now()));
    }

    private void transition(long postId, ReviewState next) {
        Post post = postRepository.findById(postId).orElseThrow();
        post.transitionTo(next);
        postRepository.flush();
    }

    private long topicId(String name) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM topic WHERE name = ?", Long.class, name);
        if (id == null) {
            throw new IllegalStateException("预设话题应由 Flyway 种入：" + name);
        }
        return id;
    }

    private User createStudent(String email, String studentId) {
        return userService.createVerifiedStudent(cryptoService.hashEmail(email), studentId);
    }

    private String bearer(User user, UserRole role) {
        userService.requireById(user.getId()).changeRole(role);
        return "Bearer " + jwtService.issueAccessToken(user.getId(), role);
    }

    private com.fasterxml.jackson.databind.JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
