package com.qinglan.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.CreateReportRequest;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 私有图片签名 URL 的端到端验收（T-30，真实 MySQL + 本地存储实现）。
 *
 * <p>核心场景：浏览器 {@code <img>} 不带 Bearer 令牌，等价游客。公开帖（APPROVED）的图 URL 保持干净、
 * 游客可取；私有帖（待审 / 下架）的图在下发 DTO 时带短时签名，<b>不带令牌也能取到</b>；签名被篡改 /
 * 过期 / 超上限一律与「不存在」同码 404；无签名时回落原有身份判定路径（T-27① 的 404 语义不失效）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageSignedUrlIntegrationTest {

    private static final String AUTHOR_EMAIL = "sign-author@qinglan.edu.cn";
    private static final String AUTHOR_STUDENT_ID = "2023013001";
    private static final String OTHER_EMAIL = "sign-other@qinglan.edu.cn";
    private static final String OTHER_STUDENT_ID = "2023013002";
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
    @DisplayName("上传接口返回的签名 URL 立即可用：模拟浏览器不带令牌取图 → 200")
    void uploadReturnsImmediatelyUsableSignedUrl() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);

        String signedUrl = upload(authorHeader);

        assertThat(signedUrl).contains("exp=").contains("sig=");
        // 不带 Authorization，等价浏览器 <img> 请求
        mockMvc.perform(get(signedUrl)).andExpect(status().isOk());
        // 干净 URL（无签名）对游客仍是 404：未绑定上传只对上传者可见
        mockMvc.perform(get(clean(signedUrl))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("APPROVED 帖图 URL 不带签名，且游客可直接取到（保持 URL 干净可缓存）")
    void approvedPostImageIsCleanAndGuestReadable() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);

        String cleanUrl = clean(upload(authorHeader));
        long postId = publish(authorHeader, cleanUrl);
        approve(postId);

        List<String> images = detailImages(null, postId);
        assertThat(images).containsExactly(cleanUrl);
        mockMvc.perform(get(cleanUrl)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("待审帖图：作者视角带签名，且该签名 URL 不带令牌也能取到；干净 URL 游客 404")
    void pendingPostImageIsSignedAndFetchableWithoutToken() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);

        String cleanUrl = clean(upload(authorHeader));
        long postId = publish(authorHeader, cleanUrl);

        // 作者看自己的待审帖：图片装配为签名 URL
        List<String> images = detailImages(authorHeader, postId);
        assertThat(images).hasSize(1);
        assertThat(images.get(0)).startsWith(cleanUrl).contains("exp=").contains("sig=");

        // 模拟浏览器 <img>（不带令牌）用签名 URL 取图 → 200
        mockMvc.perform(get(images.get(0))).andExpect(status().isOk());
        // 同一张图不带签名、不带令牌 → 游客身份路径 → 404
        mockMvc.perform(get(cleanUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("篡改签名 → 与不存在同码 404（不泄露存在性）")
    void tamperedSignatureIs404() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);

        String signed = upload(authorHeader);
        String tampered = signed.substring(0, signed.indexOf("&sig=") + 5) + "tamperedSignatureValue";

        mockMvc.perform(get(tampered))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-010"));
        // 原签名仍有效（证明只是签名被改动导致拒绝）
        mockMvc.perform(get(signed)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("只带 exp 不带 sig → 按无签名走身份路径：游客 404，作者 200")
    void partialSignatureFallsBackToIdentityPath() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);

        String cleanUrl = clean(upload(authorHeader));
        long postId = publish(authorHeader, cleanUrl);

        String onlyExp = cleanUrl + "?exp=9999999999";
        mockMvc.perform(get(onlyExp)).andExpect(status().isNotFound());
        mockMvc.perform(get(onlyExp).header("Authorization", authorHeader)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("下架帖：旧的公开（干净）URL 立即 404（T-27①），作者用签名 URL 仍可取到")
    void takenDownImageCleanUrlIs404ButSignedUrlWorks() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);

        String cleanUrl = clean(upload(authorHeader));
        long postId = publish(authorHeader, cleanUrl);
        approve(postId);
        // 放行期间公开可取
        mockMvc.perform(get(cleanUrl)).andExpect(status().isOk());

        takeDown(postId);
        // 下架后：干净 URL 对游客立即 404（T-27① 不得因签名机制失效）
        mockMvc.perform(get(cleanUrl)).andExpect(status().isNotFound());
        // 作者视角仍拿到签名 URL，且不带令牌可取（作者申诉时要能看到原图）
        List<String> images = detailImages(authorHeader, postId);
        assertThat(images.get(0)).contains("exp=").contains("sig=");
        mockMvc.perform(get(images.get(0))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("批量换取：本人未绑定上传可签发；他人私有图不签发；公开图返回原 URL；需登录")
    void refreshEndpointSignsOwnButRefusesOthers() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User other = createStudent(OTHER_EMAIL, OTHER_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);
        String otherHeader = bearer(other, UserRole.STUDENT);

        String publicClean = clean(upload(authorHeader));
        String privateClean = clean(upload(authorHeader));
        long postId = publish(authorHeader, publicClean);
        approve(postId);

        // 本人：公开图 → 原 URL；未绑定私有图 → 签名 URL
        String authorBody = refresh(authorHeader, List.of(publicClean, privateClean));
        JsonNode authorItems = readJson(authorBody).path("data");
        assertThat(authorItems.get(0).path("available").asBoolean()).isTrue();
        assertThat(authorItems.get(0).path("usableUrl").asText()).isEqualTo(publicClean);
        assertThat(authorItems.get(1).path("available").asBoolean()).isTrue();
        assertThat(authorItems.get(1).path("usableUrl").asText())
                .startsWith(privateClean).contains("sig=");

        // 他人：公开图可换取；作者的未绑定私有图不签发（available=false 且 usableUrl 为空）
        mockMvc.perform(post("/api/uploads/image-urls")
                        .header("Authorization", otherHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("urls", List.of(publicClean, privateClean)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].available").value(true))
                .andExpect(jsonPath("$.data[0].usableUrl").value(publicClean))
                .andExpect(jsonPath("$.data[1].available").value(false))
                .andExpect(jsonPath("$.data[1].usableUrl").value(nullValue()));

        // 需登录：游客一律 401
        mockMvc.perform(post("/api/uploads/image-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("urls", List.of(publicClean)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理端待审队列：PENDING 帖图签为短时 URL，不带令牌（模拟 <img>）即可取到；干净 URL 游客 404")
    void adminPendingQueueSignsPrivateImages() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);
        User admin = createStudent("sign-admin@qinglan.edu.cn", "2023013003");

        String cleanUrl = clean(upload(authorHeader));
        long postId = publish(authorHeader, cleanUrl);

        JsonNode images = pendingPostImages(admin, postId);
        assertThat(images).hasSize(1);
        String signedUrl = images.get(0).asText();
        assertThat(signedUrl).startsWith(cleanUrl).contains("exp=").contains("sig=");

        // 管理端 <img> 不带 Bearer 令牌 → 用签名 URL 可取到（修复前的破图就在这里）
        mockMvc.perform(get(signedUrl)).andExpect(status().isOk());
        // 同一张图不带签名、不带令牌 → 游客身份路径 → 404
        mockMvc.perform(get(cleanUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("管理端举报队列：APPROVED 帖图保持干净 URL，游客可直接取到（不被签名污染）")
    void adminReviewQueueKeepsApprovedImagesClean() throws Exception {
        User author = createStudent(AUTHOR_EMAIL, AUTHOR_STUDENT_ID);
        User reporter = createStudent(OTHER_EMAIL, OTHER_STUDENT_ID);
        String authorHeader = bearer(author, UserRole.STUDENT);
        String reporterHeader = bearer(reporter, UserRole.STUDENT);
        User admin = createStudent("sign-admin@qinglan.edu.cn", "2023013003");

        String cleanUrl = clean(upload(authorHeader));
        long postId = publish(authorHeader, cleanUrl);
        approve(postId);
        // 举报使该 APPROVED 帖进入举报队列（队列按「被举报目标」聚合）
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", reporterHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest(ReviewTargetType.POST, postId, ReportReason.ABUSE, null))))
                .andExpect(status().isOk());

        JsonNode images = reviewQueueImages(admin, postId);
        assertThat(images).hasSize(1);
        assertThat(images.get(0).asText()).isEqualTo(cleanUrl);
        mockMvc.perform(get(cleanUrl)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 辅助

    private String upload(String authorHeader) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG);
        String body = mockMvc.perform(multipart("/api/uploads/images")
                        .file(file)
                        .header("Authorization", authorHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body).path("data").path("url").asText();
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

    private List<String> detailImages(String header, long postId) throws Exception {
        var request = get("/api/posts/" + postId);
        if (header != null) {
            request = request.header("Authorization", header);
        }
        String body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode images = readJson(body).path("data").path("images");
        return objectMapper.convertValue(images, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, String.class));
    }

    private String refresh(String header, List<String> urls) throws Exception {
        return mockMvc.perform(post("/api/uploads/image-urls")
                        .header("Authorization", header)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("urls", urls))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 管理端待审队列里该帖的图片出参（模拟管理端渲染缩略图时读到的那串 URL） */
    private JsonNode pendingPostImages(User admin, long postId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/pending-posts")
                        .header("Authorization", bearer(admin, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode item : readJson(body).path("data").path("items")) {
            if (item.path("postId").asLong() == postId) {
                return item.path("images");
            }
        }
        throw new AssertionError("待审队列里没有帖子 " + postId);
    }

    /** 管理端举报队列里该帖的图片出参 */
    private JsonNode reviewQueueImages(User admin, long postId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/review-queue")
                        .header("Authorization", bearer(admin, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode item : readJson(body).path("data").path("items")) {
            if (item.path("targetId").asLong() == postId) {
                return item.path("images");
            }
        }
        throw new AssertionError("举报队列里没有帖子 " + postId);
    }

    /** 审核状态迁移直接用状态机（本用例只关心图片可见性，不重复验证管理端权限链路） */
    private void approve(long postId) {
        transition(postId, new ReviewState.Reviewing(1L, Instant.now()));
        transition(postId, new ReviewState.Approved(1L, Instant.now()));
    }

    private void takeDown(long postId) {
        transition(postId, new ReviewState.TakenDown(1L, "违规内容", Instant.now()));
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

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    /** 去掉签名查询串，得到干净的对外 URL */
    private static String clean(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }
}
