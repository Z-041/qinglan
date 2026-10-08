package com.qinglan.post.service;

import com.qinglan.announcement.dto.PendingAnnouncementResponse;
import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.announcement.service.AnnouncementService;
import com.qinglan.comment.dto.CommentResponse;
import com.qinglan.common.anonymous.AnonymousCodeService;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.error.PreconditionRequiredException;
import com.qinglan.common.interaction.LikeRecord;
import com.qinglan.common.interaction.LikeRepository;
import com.qinglan.common.interaction.LikeTargetType;
import com.qinglan.common.moderation.ContentGuard;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.security.Viewer;
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.common.store.InMemoryEphemeralStore;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    /** 与 LocalStorageService 返回值同构：/uploads/{yyyy}/{MM}/{32 位 hex}.{ext} */
    private static final String IMAGE_URL = "/uploads/2026/10/" + "a".repeat(32) + ".jpg";

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

    @Mock
    private AnnouncementService announcementService;

    @Mock
    private NotificationPort notificationPort;

    /** 真实内存实现：H4 的「按用户维度限流 + 用户间额度隔离」只有走真实计数才验证得到 */
    private final EphemeralStore ephemeralStore = new InMemoryEphemeralStore();

    private PostService postService;

    /** 默认放行的硬拦截桩；需要拦截的用例自行构造一个会抛的守卫 */
    private static final ContentGuard ALLOW_ALL = text -> {
    };

    @BeforeEach
    void setUp() {
        postService = newService(ephemeralStore);
    }

    private PostService newService(EphemeralStore store) {
        return new PostService(postRepository, postImageRepository, topicRepository,
                likeRepository, anonymousCodeService, userService, auditLogService,
                new PostProperties(60, 9), ALLOW_ALL, announcementService, notificationPort, store);
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
                new CreatePostRequest(TOPIC_ID, " 出个二手自行车 ", CONTACT, List.of(IMAGE_URL)));

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
    @DisplayName("被封禁的账号不允许发帖（写入口统一校验，不依赖令牌是否过期）")
    void shouldRejectWhenBanned() {
        when(userService.requireUsableById(AUTHOR_ID))
                .thenThrow(new BizException(ErrorCode.USER_BANNED));

        assertThatThrownBy(() -> postService.create(AUTHOR_ID, new CreatePostRequest(TOPIC_ID, "正文", null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
    }

    @Test
    @DisplayName("本地硬拦截已接入发布链路，命中即拒绝且不落库")
    void shouldRejectWhenModeratorBlocks() {
        stubAuthor(activeAuthor());
        when(topicRepository.findById(TOPIC_ID))
                .thenReturn(Optional.of(TestFixtures.topic(TOPIC_ID, "吐槽", false)));
        ContentGuard blocking = text -> {
            throw new BizException(ErrorCode.CONTENT_REJECTED);
        };
        PostService strict = new PostService(postRepository, postImageRepository, topicRepository,
                likeRepository, anonymousCodeService, userService, auditLogService,
                new PostProperties(60, 9), blocking, announcementService, notificationPort, ephemeralStore);

        assertThatThrownBy(() -> strict.create(AUTHOR_ID, new CreatePostRequest(TOPIC_ID, "正文", null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CONTENT_REJECTED);
        verifyNoInteractions(postRepository);
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
    @DisplayName("存在未读紧急公告时拒绝发帖并返回 428，且完全不落库")
    void shouldBlockPostWhenUnreadUrgentAnnouncement() {
        stubAuthor(activeAuthor());
        when(announcementService.findPendingUrgent(AUTHOR_ID)).thenReturn(Optional.of(
                new PendingAnnouncementResponse(1L, "紧急公告", "公告正文", AnnouncementLevel.URGENT,
                        5, false, false, Instant.parse("2026-10-07T00:00:00Z"))));

        assertThatThrownBy(() -> postService.create(AUTHOR_ID, new CreatePostRequest(TOPIC_ID, "正文", null, null)))
                .isInstanceOf(PreconditionRequiredException.class)
                .extracting(ex -> ((PreconditionRequiredException) ex).errorCode())
                .isEqualTo(ErrorCode.PRECONDITION_REQUIRED);

        // 强制阅读真强制：拦截在落库之前
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("发帖 gate 只问紧急公告：普通公告不进拦截口径（设计方案 5.4）")
    void shouldAskOnlyUrgentAnnouncementWhenCreating() {
        stubAuthor(activeAuthor());
        when(topicRepository.findById(TOPIC_ID))
                .thenReturn(Optional.of(TestFixtures.topic(TOPIC_ID, "问答", false)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            TestFixtures.setId(saved, 9L);
            return saved;
        });

        postService.create(AUTHOR_ID, new CreatePostRequest(TOPIC_ID, "正文", null, null));

        // 合同断言：只能问 URGENT 口径。若改回不限级别的 findPending，
        // 一条未读普通公告就会挡住全体用户发帖
        verify(announcementService).findPendingUrgent(AUTHOR_ID);
        verify(announcementService, never()).findPending(any(), any());
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
    @DisplayName("G：图片来源必须是本服务生成的格式，伪协议、外链与伪造的 /uploads 路径一律拒绝")
    void shouldRejectForeignImageUrls() {
        stubAuthor(activeAuthor());
        when(topicRepository.findById(TOPIC_ID))
                .thenReturn(Optional.of(TestFixtures.topic(TOPIC_ID, "吐槽", false)));

        List<String> bad = List.of(
                "javascript:alert(1)",
                "https://evil.example/x.jpg",
                "data:image/png;base64,AAAA",
                // 只判 /uploads/ 前缀会放过这两条：路径段数不对、文件名不是 32 位 hex
                "/uploads/anything",
                "/uploads/2026/10/not-a-uuid.jpg",
                "/uploads/2026/10/" + "a".repeat(32) + ".gif");
        for (String url : bad) {
            assertThatThrownBy(() -> postService.create(AUTHOR_ID,
                    new CreatePostRequest(TOPIC_ID, "正文", null, List.of(url))))
                    .as("应拒绝非法图片地址：%s", url)
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

    // -------------------------------------------------------- H4 联系方式审计限流

    @Test
    @DisplayName("H4 限流：同一用户在窗口内超限后不再写 CONTACT_VIEW 审计，但详情仍正常返回")
    void shouldStopAuditingAfterRateLimitButStillReturnDetail() {
        long viewerId = 8L;
        stubDetailDependencies();
        stubPostAggregates(viewerId);

        for (int i = 0; i < PostService.CONTACT_VIEW_MAX_PER_WINDOW; i++) {
            postService.detail(POST_ID, new Viewer(viewerId));
        }
        verify(auditLogService, times(PostService.CONTACT_VIEW_MAX_PER_WINDOW))
                .record(AuditTargetType.POST, POST_ID, AuditAction.CONTACT_VIEW, viewerId, null);

        // 超出上限：仍然正常返回详情（不抛异常、不返回 4xx），只是不再写审计
        PostDetailResponse response = postService.detail(POST_ID, new Viewer(viewerId));

        assertThat(response.contact()).isEqualTo(CONTACT);
        verify(auditLogService, times(PostService.CONTACT_VIEW_MAX_PER_WINDOW))
                .record(AuditTargetType.POST, POST_ID, AuditAction.CONTACT_VIEW, viewerId, null);
    }

    @Test
    @DisplayName("H4 限流：不同用户额度独立，甲超限不影响乙")
    void shouldIsolateContactViewRateLimitPerUser() {
        long flooder = 8L;
        long other = 9L;
        stubDetailDependencies();
        stubPostAggregates(flooder);
        stubPostAggregates(other);

        // 甲打满额度并越界一次
        for (int i = 0; i <= PostService.CONTACT_VIEW_MAX_PER_WINDOW; i++) {
            postService.detail(POST_ID, new Viewer(flooder));
        }
        verify(auditLogService, times(PostService.CONTACT_VIEW_MAX_PER_WINDOW))
                .record(AuditTargetType.POST, POST_ID, AuditAction.CONTACT_VIEW, flooder, null);

        // 乙的额度与甲无关，照样写审计
        postService.detail(POST_ID, new Viewer(other));

        verify(auditLogService).record(AuditTargetType.POST, POST_ID, AuditAction.CONTACT_VIEW, other, null);
    }

    @Test
    @DisplayName("H4：看自己的帖子 / 未认证 / 联系方式不可见，既不写审计也不占用限流额度")
    void shouldNotAuditNorConsumeQuotaWhenNoAuditNeeded() {
        // 用探针存储验证「不消耗额度」：这些场景连 increment 都不该发生
        EphemeralStore probe = mock(EphemeralStore.class);
        PostService service = newService(probe);
        stubDetailDependencies();
        stubPostAggregates(AUTHOR_ID);

        // 作者看自己的帖子
        service.detail(POST_ID, new Viewer(AUTHOR_ID));
        // 游客看
        service.detail(POST_ID, Viewer.anonymous());
        // 联系方式被隐藏时他人看不到
        Post hidden = TestFixtures.approvedPost(POST_ID, AUTHOR_ID, TOPIC_ID, CONTACT);
        hidden.hideContact();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(hidden));
        service.detail(POST_ID, new Viewer(8L));

        verifyNoInteractions(auditLogService);
        verifyNoInteractions(probe);
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

    // ------------------------------------------------------------------ 点赞通知

    @Test
    @DisplayName("点赞仅在确为新建点赞记录时通知帖主：类型 LIKE、收件人为作者")
    void shouldNotifyAuthorOnNewLike() {
        long likerId = 8L;
        when(userService.requireUsableById(likerId)).thenReturn(activeAuthor());
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(TestFixtures.approvedPost(POST_ID, AUTHOR_ID, TOPIC_ID, null)));
        when(likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, POST_ID, likerId))
                .thenReturn(Optional.empty());
        when(likeRepository.countByTargetTypeAndTargetId(LikeTargetType.POST, POST_ID)).thenReturn(1L);

        postService.like(POST_ID, likerId);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPort).notify(eq(AUTHOR_ID), eq(likerId), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.LIKE);
        assertThat(captor.getValue().title()).isEqualTo("有人赞了你的帖子");
        assertThat(captor.getValue().link()).isEqualTo("/posts/" + POST_ID);
    }

    @Test
    @DisplayName("重复点赞（幂等命中已有记录）不重复发通知")
    void shouldNotNotifyOnDuplicateLike() {
        long likerId = 8L;
        when(userService.requireUsableById(likerId)).thenReturn(activeAuthor());
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(TestFixtures.approvedPost(POST_ID, AUTHOR_ID, TOPIC_ID, null)));
        when(likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, POST_ID, likerId))
                .thenReturn(Optional.of(LikeRecord.of(LikeTargetType.POST, POST_ID, likerId)));
        when(likeRepository.countByTargetTypeAndTargetId(LikeTargetType.POST, POST_ID)).thenReturn(1L);

        postService.like(POST_ID, likerId);

        verify(likeRepository, never()).save(any());
        verify(notificationPort, never()).notify(any(), any(), any());
    }

    // ------------------------------------------------------------------ 辅助

    private void stubAuthor(User author) {
        when(userService.requireUsableById(AUTHOR_ID)).thenReturn(author);
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
