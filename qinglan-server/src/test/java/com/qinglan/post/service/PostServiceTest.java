package com.qinglan.post.service;

import com.qinglan.comment.dto.CommentResponse;
import com.qinglan.common.anonymous.AnonymousCodeService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.interaction.LikeRepository;
import com.qinglan.common.interaction.LikeTargetType;
import com.qinglan.common.security.Viewer;
import com.qinglan.post.config.PostProperties;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.post.dto.PostDetailResponse;
import com.qinglan.post.dto.PostSummaryResponse;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostImage;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.RiskLevel;
import com.qinglan.post.repository.PostImageRepository;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.TopicRepository;
import com.qinglan.review.entity.AuditAction;
import com.qinglan.review.entity.AuditTargetType;
import com.qinglan.review.service.AuditLogService;
import com.qinglan.support.TestFixtures;
import com.qinglan.user.entity.User;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 帖子服务的单元测试，重点是不变量 #1（代号与 authorId 不同时外泄）、
 * #2（联系方式服务端脱敏）与 #5（审核状态不跳态）。
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final long AUTHOR_ID = 7L;
    private static final long POST_ID = 100L;
    private static final long TOPIC_ID = 3L;
    private static final String CONTACT = "13800000000";
    private static final String SALT = "salt";

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private AnonymousCodeService anonymousCodeService;

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, postImageRepository, topicRepository,
                likeRepository, anonymousCodeService, userService, auditLogService,
                new PostProperties(60, 9));
    }

    // ------------------------------------------------------------------ 发布

    @Test
    @DisplayName("发布：落 Pending 待审，拿到主键后回填真实匿名代号")
    void shouldCreatePendingPostWithDerivedAnonymousCode() {
        stubAuthor(activeAuthor());
        when(topicRepository.findById(TOPIC_ID))
                .thenReturn(Optional.of(TestFixtures.topic(TOPIC_ID, "二手", true)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            TestFixtures.setId(post, POST_ID);
            return post;
        });
        when(anonymousCodeService.deriveCode(POST_ID, AUTHOR_ID, SALT)).thenReturn("银杏#1a2b");

        PostDetailResponse response = postService.create(AUTHOR_ID,
                new CreatePostRequest(TOPIC_ID, " 出个二手自行车 ", CONTACT, List.of("/uploads/a.jpg")));

        // 代号是插入后回填的派生值，不是插入占位符
        assertThat(response.anonymousCode()).isEqualTo("银杏#1a2b");
        assertThat(response.mine()).isTrue();
        assertThat(response.content()).isEqualTo("出个二手自行车");

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PostStatus.PENDING);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostImage>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(postImageRepository).saveAll(imagesCaptor.capture());
        assertThat(imagesCaptor.getValue()).extracting(PostImage::getSortOrder).containsExactly(0);
    }

    @Test
    @DisplayName("信用分低于阈值不允许发帖")
    void shouldRejectWhenCreditTooLow() {
        User author = activeAuthor();
        author.adjustCredit(-50);
        stubAuthor(author);

        assertThatThrownBy(() -> postService.create(AUTHOR_ID, new CreatePostRequest(TOPIC_ID, "正文", null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CREDIT_TOO_LOW);
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("被封禁的账号不允许发帖")
    void shouldRejectWhenBanned() {
        User author = activeAuthor();
        author.banUntil(Instant.now().plus(Duration.ofDays(3)));
        stubAuthor(author);

        assertThatThrownBy(() -> postService.create(AUTHOR_ID, new CreatePostRequest(TOPIC_ID, "正文", null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
    }

    @Test
    @DisplayName("话题要求留联系方式时必须填写")
    void shouldRequireContactWhenTopicDemandsIt() {
        stubAuthor(activeAuthor());
        when(topicRepository.findById(TOPIC_ID))
                .thenReturn(Optional.of(TestFixtures.topic(TOPIC_ID, "失物招领", true)));

        assertThatThrownBy(() -> postService.create(AUTHOR_ID,
                new CreatePostRequest(TOPIC_ID, "捡到一张校园卡", "   ", null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CONTACT_REQUIRED);
    }

    @Test
    @DisplayName("话题不存在或已停用时拒绝发布")
    void shouldRejectUnknownTopic() {
        stubAuthor(activeAuthor());
        when(topicRepository.findById(TOPIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.create(AUTHOR_ID, new CreatePostRequest(TOPIC_ID, "正文", null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.TOPIC_NOT_FOUND);
    }

    @Test
    @DisplayName("图片来源必须是自己上传的相对路径，伪协议与外链一律拒绝")
    void shouldRejectForeignImageUrls() {
        stubAuthor(activeAuthor());
        when(topicRepository.findById(TOPIC_ID))
                .thenReturn(Optional.of(TestFixtures.topic(TOPIC_ID, "吐槽", false)));

        for (String bad : List.of("javascript:alert(1)", "https://evil.example/x.jpg", "data:image/png;base64,AAAA")) {
            assertThatThrownBy(() -> postService.create(AUTHOR_ID,
                    new CreatePostRequest(TOPIC_ID, "正文", null, List.of(bad))))
                    .as("应拒绝非法图片地址：%s", bad)
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).errorCode())
                    .isEqualTo(ErrorCode.UPLOAD_INVALID);
        }
    }

    // -------------------------------------------------------- 不变量 #2

    @Test
    @DisplayName("不变量 #2：游客看详情拿不到联系方式明文，也不写查看审计")
    void shouldHideContactFromAnonymousViewer() {
        stubDetailDependencies();
        stubPostAggregates(null);

        PostDetailResponse response = postService.detail(POST_ID, Viewer.anonymous());

        assertThat(response.contact()).isNull();
        assertThat(response.contactVisible()).isFalse();
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("不变量 #2：认证的非作者能拿到联系方式，且写入 CONTACT_VIEW 审计日志")
    void shouldReturnContactAndAuditForAuthenticatedViewer() {
        stubDetailDependencies();
        stubPostAggregates(8L);

        PostDetailResponse response = postService.detail(POST_ID, new Viewer(8L));

        assertThat(response.contact()).isEqualTo(CONTACT);
        assertThat(response.contactVisible()).isTrue();
        verify(auditLogService).record(AuditTargetType.POST, POST_ID, AuditAction.CONTACT_VIEW, 8L, null);
    }

    @Test
    @DisplayName("不变量 #2：作者看自己的帖子拿到联系方式，但不产生查看审计")
    void shouldNotAuditWhenAuthorViewsOwnContact() {
        stubDetailDependencies();
        stubPostAggregates(AUTHOR_ID);

        PostDetailResponse response = postService.detail(POST_ID, new Viewer(AUTHOR_ID));

        assertThat(response.contact()).isEqualTo(CONTACT);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("未审核通过的帖子对外一律按不存在返回，不泄露存在性")
    void shouldHidePendingPostFromOthers() {
        Post pending = Post.draft(AUTHOR_ID, TOPIC_ID, "待审正文", null);
        pending.submit(RiskLevel.LOW);
        TestFixtures.setId(pending, POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> postService.detail(POST_ID, Viewer.anonymous()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    // -------------------------------------------------------- 不变量 #5

    @Test
    @DisplayName("不变量 #5：放行需经 Reviewing 走到 Approved，并写审计日志")
    void shouldApproveThroughReviewing() {
        Post pending = Post.draft(AUTHOR_ID, TOPIC_ID, "待审正文", null);
        pending.submit(RiskLevel.LOW);
        TestFixtures.setId(pending, POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pending));

        postService.approve(POST_ID, 9L);

        assertThat(pending.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(pending.getReviewBy()).isEqualTo(9L);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID, AuditAction.POST_APPROVE, 9L, null);
    }

    @Test
    @DisplayName("不变量 #5：已通过的帖子再次放行属非法迁移，且不留审计")
    void shouldRejectApproveWhenAlreadyApproved() {
        stubApprovedPost();

        assertThatThrownBy(() -> postService.approve(POST_ID, 9L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("驳回写入理由，作者在详情里能看到该理由")
    void shouldRejectWithReasonVisibleToAuthor() {
        Post pending = Post.draft(AUTHOR_ID, TOPIC_ID, "待审正文", null);
        pending.submit(RiskLevel.LOW);
        TestFixtures.setId(pending, POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pending));

        postService.reject(POST_ID, 9L, "正文含他人手机号");

        assertThat(pending.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(pending.getReviewReason()).isEqualTo("正文含他人手机号");
        verify(auditLogService).record(AuditTargetType.POST, POST_ID, AuditAction.POST_REJECT, 9L, "正文含他人手机号");
    }

    // -------------------------------------------------------- 不变量 #1

    @Test
    @DisplayName("不变量 #1：对外 DTO 只给匿名代号，绝不同时暴露 authorId")
    void shouldNotExposeAuthorIdInAnyDto() {
        assertThat(componentNames(PostSummaryResponse.class))
                .contains("anonymousCode")
                .doesNotContain("authorId");
        assertThat(componentNames(PostDetailResponse.class))
                .contains("anonymousCode")
                .doesNotContain("authorId");
        assertThat(componentNames(CommentResponse.class))
                .contains("anonymousCode")
                .doesNotContain("authorId");
    }

    @Test
    @DisplayName("不变量 #1：同一作者在不同帖子下的代号不同")
    void shouldDeriveDifferentCodePerPost() {
        AnonymousCodeService real = new AnonymousCodeService();

        String first = real.deriveCode(1L, AUTHOR_ID, SALT);
        String second = real.deriveCode(2L, AUTHOR_ID, SALT);

        assertThat(first).isNotEqualTo(second);
    }

    // ------------------------------------------------------------------ 辅助

    private void stubAuthor(User author) {
        when(userService.requireById(AUTHOR_ID)).thenReturn(author);
    }

    private void stubApprovedPost() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(TestFixtures.approvedPost(POST_ID, AUTHOR_ID, TOPIC_ID, CONTACT)));
    }

    /** 详情接口额外要读话题与图片；拆开是因为 approve/reject 用不到这些桩 */
    private void stubDetailDependencies() {
        stubApprovedPost();
        when(topicRepository.findById(TOPIC_ID))
                .thenReturn(Optional.of(TestFixtures.topic(TOPIC_ID, "二手", true)));
        when(postImageRepository.findByPostIdInOrderBySortOrderAsc(List.of(POST_ID))).thenReturn(List.of());
    }

    private void stubPostAggregates(Long viewerId) {
        when(likeRepository.countByTargetTypeAndTargetId(LikeTargetType.POST, POST_ID)).thenReturn(0L);
        if (viewerId != null) {
            when(likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, POST_ID, viewerId))
                    .thenReturn(Optional.empty());
        }
    }

    private static User activeAuthor() {
        return User.register("emailHash", "studentIdHash", "enc", SALT);
    }

    private static Set<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
