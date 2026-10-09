package com.qinglan.post.service;

import com.qinglan.common.storage.ImageUrlSigner;
import com.qinglan.common.storage.StorageProperties;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 图片访问判定的规则矩阵（T-27①）：公开 / 私有 × 上传者 / 作者 / 他人 / 游客 × 干净 / 签名 URL。
 *
 * <p>这是「图片可见性口径」的唯一断言点，此前分散在三处实现里，现在只在这里验规则。</p>
 */
@ExtendWith(MockitoExtension.class)
class ImageAccessServiceTest {

    private static final Long UPLOADER = 7L;
    private static final Long OTHER = 8L;
    private static final long POST_ID = 100L;
    private static final String OBJECT_KEY = "2026/10/" + "a".repeat(32) + ".jpg";
    private static final String CLEAN_URL = "/uploads/" + OBJECT_KEY;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private PostRepository postRepository;

    private final ImageUrlSigner signer = new ImageUrlSigner(
            new StorageProperties(null, null, null, "unit-test-image-signing-secret", null),
            new MockEnvironment());

    private ImageAccessService service;

    @BeforeEach
    void setUp() {
        service = new ImageAccessService(signer, uploadedFileRepository, postRepository);
    }

    // ---------- forPost：已知状态、免查库 ----------

    @Test
    @DisplayName("已通过帖：图片给干净原 URL，可读")
    void forPostApprovedKeepsCleanUrl() {
        List<ImageAccessService.ImageAccess> result = service.forPost(PostStatus.APPROVED, List.of(CLEAN_URL));

        assertThat(result).singleElement().satisfies(access -> {
            assertThat(access.cleanUrl()).isEqualTo(CLEAN_URL);
            assertThat(access.readable()).isTrue();
            assertThat(access.usableUrl()).isEqualTo(CLEAN_URL);
        });
        verifyNoInteractions(uploadedFileRepository, postRepository);
    }

    @Test
    @DisplayName("非通过帖：图片签为短时 URL，可读")
    void forPostNonApprovedSigns() {
        List<ImageAccessService.ImageAccess> result = service.forPost(PostStatus.PENDING, List.of(CLEAN_URL));

        assertThat(result).singleElement().satisfies(access -> {
            assertThat(access.cleanUrl()).isEqualTo(CLEAN_URL);
            assertThat(access.readable()).isTrue();
            assertThat(access.usableUrl()).startsWith(CLEAN_URL).contains("exp=").contains("sig=");
        });
    }

    @Test
    @DisplayName("无归属（状态为 null）：按私有处理，签为短时 URL")
    void forPostNullStatusSigns() {
        List<ImageAccessService.ImageAccess> result = service.forPost(null, List.of(CLEAN_URL));

        assertThat(result).singleElement()
                .satisfies(access -> assertThat(access.usableUrl()).startsWith(CLEAN_URL).contains("sig="));
    }

    @Test
    @DisplayName("空/无图：返回空列表，不查库")
    void forPostEmptyReturnsEmpty() {
        assertThat(service.forPost(PostStatus.APPROVED, List.of())).isEmpty();
        assertThat(service.forPost(PostStatus.PENDING, null)).isEmpty();
        verifyNoInteractions(uploadedFileRepository, postRepository);
    }

    // ---------- forUpload：未绑定上传 ----------

    @Test
    @DisplayName("未绑定上传：恒签名、可读（调用方即上传者）")
    void forUploadSigns() {
        ImageAccessService.ImageAccess access = service.forUpload(CLEAN_URL);

        assertThat(access.cleanUrl()).isEqualTo(CLEAN_URL);
        assertThat(access.readable()).isTrue();
        assertThat(access.usableUrl()).startsWith(CLEAN_URL).contains("exp=").contains("sig=");
        verifyNoInteractions(uploadedFileRepository, postRepository);
    }

    // ---------- resolve：按 objectKey 反查 ----------

    @Test
    @DisplayName("未绑定：上传者本人可读（签名），他人不可读（不签发）")
    void resolveUnbound() {
        when(uploadedFileRepository.findByObjectKey(OBJECT_KEY))
                .thenReturn(Optional.of(UploadedFile.of(UPLOADER, OBJECT_KEY)));

        ImageAccessService.ImageAccess mine = service.resolve(CLEAN_URL, UPLOADER);
        assertThat(mine.readable()).isTrue();
        assertThat(mine.usableUrl()).startsWith(CLEAN_URL).contains("sig=");

        ImageAccessService.ImageAccess other = service.resolve(CLEAN_URL, OTHER);
        assertThat(other.readable()).isFalse();
        assertThat(other.usableUrl()).isNull();
        assertThat(other.cleanUrl()).isEqualTo(CLEAN_URL);
    }

    @Test
    @DisplayName("已通过帖：任何 viewer（含游客）可读，给干净 URL")
    void resolveBoundApprovedIsPublic() {
        stubBoundPost(PostStatus.APPROVED, true);

        ImageAccessService.ImageAccess guest = service.resolve(CLEAN_URL, null);
        assertThat(guest.readable()).isTrue();
        assertThat(guest.usableUrl()).isEqualTo(CLEAN_URL);
    }

    @Test
    @DisplayName("非通过帖：作者可读（签名），他人与游客不可读")
    void resolveBoundNonApprovedAuthorOnly() {
        stubBoundPost(PostStatus.TAKEN_DOWN, true);

        ImageAccessService.ImageAccess author = service.resolve(CLEAN_URL, UPLOADER);
        assertThat(author.readable()).isTrue();
        assertThat(author.usableUrl()).startsWith(CLEAN_URL).contains("sig=");

        // 另起一个「非作者」的帖子桩：canBeViewedBy 为 false
        stubBoundPost(PostStatus.TAKEN_DOWN, false);
        ImageAccessService.ImageAccess other = service.resolve(CLEAN_URL, OTHER);
        assertThat(other.readable()).isFalse();
        assertThat(other.usableUrl()).isNull();
    }

    @Test
    @DisplayName("地址非法：原样回显、不可读、不签发，且不查库")
    void resolveIllegalUrl() {
        ImageAccessService.ImageAccess access = service.resolve("/uploads/evil/../x.jpg", OTHER);

        assertThat(access.cleanUrl()).isEqualTo("/uploads/evil/../x.jpg");
        assertThat(access.readable()).isFalse();
        assertThat(access.usableUrl()).isNull();
        verifyNoInteractions(uploadedFileRepository, postRepository);
    }

    @Test
    @DisplayName("对象未登记：不可读、不签发")
    void resolveUnknownObject() {
        when(uploadedFileRepository.findByObjectKey(OBJECT_KEY)).thenReturn(Optional.empty());

        ImageAccessService.ImageAccess access = service.resolve(CLEAN_URL, OTHER);

        assertThat(access.cleanUrl()).isEqualTo(CLEAN_URL);
        assertThat(access.readable()).isFalse();
        assertThat(access.usableUrl()).isNull();
    }

    @Test
    @DisplayName("绑定帖已不存在：按不可读处理（兜底）")
    void resolveBoundPostMissing() {
        UploadedFile bound = UploadedFile.of(UPLOADER, OBJECT_KEY);
        bound.bindTo(POST_ID);
        when(uploadedFileRepository.findByObjectKey(OBJECT_KEY)).thenReturn(Optional.of(bound));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThat(service.resolve(CLEAN_URL, UPLOADER).readable()).isFalse();
    }

    // ---------- canDeliver：下发授权 ----------

    @Test
    @DisplayName("合法签名：不依赖 viewer 直接放行（游客也 true）")
    void canDeliverValidSignature() {
        String signed = signer.sign(CLEAN_URL);

        assertThat(service.canDeliver(CLEAN_URL, param(signed, "exp"), param(signed, "sig"), null)).isTrue();
        verifyNoInteractions(uploadedFileRepository, postRepository);
    }

    @Test
    @DisplayName("签名被篡改：拒绝")
    void canDeliverTamperedSignature() {
        String signed = signer.sign(CLEAN_URL);

        assertThat(service.canDeliver(CLEAN_URL, param(signed, "exp"), "tampered", null)).isFalse();
    }

    @Test
    @DisplayName("无签名：回落身份判定——可读者放行，不可读者拒绝")
    void canDeliverFallsBackToIdentity() {
        stubBoundPost(PostStatus.APPROVED, true);
        assertThat(service.canDeliver(CLEAN_URL, null, null, null)).isTrue();

        stubBoundPost(PostStatus.PENDING, false);
        assertThat(service.canDeliver(CLEAN_URL, null, null, OTHER)).isFalse();
    }

    /** 造一个「已绑定」的帖子桩：file 绑定 POST_ID，post 状态与可见性由参数决定。 */
    private void stubBoundPost(PostStatus status, boolean viewable) {
        UploadedFile file = UploadedFile.of(UPLOADER, OBJECT_KEY);
        file.bindTo(POST_ID);
        when(uploadedFileRepository.findByObjectKey(OBJECT_KEY)).thenReturn(Optional.of(file));
        Post post = mock(Post.class);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(post.canBeViewedBy(any())).thenReturn(viewable);
        if (viewable) {
            when(post.getStatus()).thenReturn(status);
        }
    }

    private static String param(String url, String name) {
        Matcher matcher = Pattern.compile("[?&]" + name + "=([^&]+)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
}
