package com.qinglan.comment.service;

import com.qinglan.comment.dto.CommentResponse;
import com.qinglan.comment.dto.CreateCommentRequest;
import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.entity.CommentStatus;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.anonymous.AnonymousCodeService;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.interaction.LikeCount;
import com.qinglan.common.interaction.LikeRepository;
import com.qinglan.common.interaction.LikeTargetType;
import com.qinglan.common.moderation.ContentGuard;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.security.Viewer;
import com.qinglan.post.entity.Post;
import com.qinglan.post.repository.PostRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评论服务的单元测试。评论是先发后审、不进主帖状态机，所以这里盯的是
 * 「两级结构约束」与「post.comment_count 冗余计数与实际可见评论数一致」。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final long POST_ID = 100L;
    private static final long AUTHOR_ID = 7L;
    private static final long ROOT_ID = 10L;
    private static final long REPLY_ID = 11L;
    private static final String SALT = "salt";

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private AnonymousCodeService anonymousCodeService;

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationPort notificationPort;

    private CommentService commentService;

    /** 默认放行的硬拦截桩；需要拦截的用例自行构造一个会抛的守卫 */
    private static final ContentGuard ALLOW_ALL = text -> {
    };

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, postRepository, likeRepository,
                anonymousCodeService, userService, auditLogService, notificationPort, ALLOW_ALL);
    }

    @Test
    @DisplayName("发表一级评论：代号与帖子同源派生，并让 post.comment_count 原子加一")
    void shouldCreateRootCommentAndBumpCount() {
        Post post = visiblePost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(userService.requireUsableById(AUTHOR_ID))
                .thenReturn(User.register("emailHash", "studentIdHash", "enc", SALT));
        when(anonymousCodeService.deriveCode(POST_ID, AUTHOR_ID, SALT)).thenReturn("青竹#0f0f");
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            TestFixtures.setId(comment, ROOT_ID);
            return comment;
        });

        CommentResponse response = commentService.create(POST_ID, AUTHOR_ID,
                new CreateCommentRequest(" 同问 ", null));

        assertThat(response.id()).isEqualTo(ROOT_ID);
        assertThat(response.content()).isEqualTo("同问");
        assertThat(response.anonymousCode()).isEqualTo("青竹#0f0f");
        assertThat(response.mine()).isTrue();
        assertThat(response.replies()).isEmpty();
        verify(postRepository).adjustCommentCount(POST_ID, 1);
    }

    @Test
    @DisplayName("发表评论通知帖主：收件人为帖主、类型 COMMENT，且 body 不含评论者可关联身份（不变量 #1）")
    void shouldNotifyPostAuthorOnComment() {
        long commenterId = 8L;
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(visiblePost()));
        when(userService.requireUsableById(commenterId))
                .thenReturn(User.register("emailHash", "studentIdHash", "enc", SALT));
        when(anonymousCodeService.deriveCode(POST_ID, commenterId, SALT)).thenReturn("青竹#0f0f");
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            TestFixtures.setId(comment, ROOT_ID);
            return comment;
        });

        commentService.create(POST_ID, commenterId, new CreateCommentRequest("同问", null));

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPort).notify(eq(AUTHOR_ID), eq(commenterId), captor.capture());
        NotificationMessage message = captor.getValue();
        assertThat(message.type()).isEqualTo(NotificationType.COMMENT);
        assertThat(message.title()).isEqualTo("有人评论了你的帖子");
        assertThat(message.body()).isEqualTo("同问");
        assertThat(message.link()).isEqualTo("/posts/" + POST_ID);
        // 不变量 #1：body 只放评论文本，绝不带评论者代号等可关联字段
        assertThat(message.body()).doesNotContain("青竹#0f0f");
    }

    @Test
    @DisplayName("只支持两级：回复必须落在同一帖的一级评论下")
    void shouldRejectReplyToForeignOrNestedComment() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(visiblePost()));
        // 父评论属于另一个帖子
        when(commentRepository.findById(ROOT_ID))
                .thenReturn(Optional.of(comment(ROOT_ID, 999L, null, AUTHOR_ID)));

        assertThatThrownBy(() -> commentService.create(POST_ID, AUTHOR_ID, new CreateCommentRequest("回复", ROOT_ID)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);

        // 父评论本身是回复，会形成三级嵌套
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, POST_ID, ROOT_ID, AUTHOR_ID)));

        assertThatThrownBy(() -> commentService.create(POST_ID, AUTHOR_ID, new CreateCommentRequest("回复", REPLY_ID)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("给不可见的帖子评论按「帖子不存在」处理")
    void shouldRejectCommentOnInvisiblePost() {
        Post pending = Post.draft(AUTHOR_ID, 3L, "待审", null);
        pending.submit(com.qinglan.post.entity.RiskLevel.LOW);
        TestFixtures.setId(pending, POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> commentService.create(POST_ID, 8L, new CreateCommentRequest("评论", null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("删除一级评论时连同其回复一并移除，并按实际条数原子扣减计数")
    void shouldCascadeRemoveRepliesAndDecreaseCount() {
        when(commentRepository.findById(ROOT_ID))
                .thenReturn(Optional.of(comment(ROOT_ID, POST_ID, null, AUTHOR_ID)));
        when(commentRepository.findByParentIdInAndStatusOrderByCreatedAtAsc(List.of(ROOT_ID), CommentStatus.PUBLISHED))
                .thenReturn(List.of(
                        comment(REPLY_ID, POST_ID, ROOT_ID, 8L),
                        comment(REPLY_ID + 1, POST_ID, ROOT_ID, 9L)));

        commentService.delete(ROOT_ID, AUTHOR_ID);

        // 一级评论 + 两条回复，共扣 3
        verify(postRepository).adjustCommentCount(POST_ID, -3);
    }

    @Test
    @DisplayName("R-15：删除评论时把该评论（含连带移除的回复）的点赞行一并清掉")
    void shouldPurgeLikesOfRemovedComments() {
        when(commentRepository.findById(ROOT_ID))
                .thenReturn(Optional.of(comment(ROOT_ID, POST_ID, null, AUTHOR_ID)));
        when(commentRepository.findByParentIdInAndStatusOrderByCreatedAtAsc(List.of(ROOT_ID), CommentStatus.PUBLISHED))
                .thenReturn(List.of(comment(REPLY_ID, POST_ID, ROOT_ID, 8L)));

        commentService.delete(ROOT_ID, AUTHOR_ID);

        verify(likeRepository).deleteByTargets(LikeTargetType.COMMENT, List.of(ROOT_ID, REPLY_ID));
    }

    @Test
    @DisplayName("不能删除他人的评论")
    void shouldRejectDeletingOthersComment() {
        when(commentRepository.findById(ROOT_ID))
                .thenReturn(Optional.of(comment(ROOT_ID, POST_ID, null, AUTHOR_ID)));

        assertThatThrownBy(() -> commentService.delete(ROOT_ID, 8L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_CONTENT_OWNER);
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("管理员移除评论：与自删共用同一套清理，并必须写审计（不变量 #7）")
    void shouldRemoveCommentByAdminWithAudit() {
        long operatorId = 99L;
        when(commentRepository.findById(ROOT_ID))
                .thenReturn(Optional.of(comment(ROOT_ID, POST_ID, null, AUTHOR_ID)));
        when(commentRepository.findByParentIdInAndStatusOrderByCreatedAtAsc(List.of(ROOT_ID), CommentStatus.PUBLISHED))
                .thenReturn(List.of(comment(REPLY_ID, POST_ID, ROOT_ID, 8L)));

        commentService.removeByAdmin(ROOT_ID, operatorId, "人身攻击");

        verify(postRepository).adjustCommentCount(POST_ID, -2);
        verify(likeRepository).deleteByTargets(LikeTargetType.COMMENT, List.of(ROOT_ID, REPLY_ID));
        verify(auditLogService).record(AuditTargetType.COMMENT, ROOT_ID,
                AuditAction.COMMENT_REMOVE, operatorId, "人身攻击");
    }

    @Test
    @DisplayName("管理员移除不存在的评论按「评论不存在」处理，且不留审计")
    void shouldRejectRemovingMissingComment() {
        when(commentRepository.findById(ROOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.removeByAdmin(ROOT_ID, 99L, "违规"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        verifyNoInteractions(postRepository, auditLogService);
    }

    @Test
    @DisplayName("重复移除同一条评论是幂等的：不二次扣减计数，也不重复留痕")
    void shouldIgnoreRemovingAlreadyRemovedComment() {
        Comment removed = comment(ROOT_ID, POST_ID, null, AUTHOR_ID);
        removed.remove();
        when(commentRepository.findById(ROOT_ID)).thenReturn(Optional.of(removed));

        commentService.removeByAdmin(ROOT_ID, 99L, "违规");

        verifyNoInteractions(postRepository, likeRepository, auditLogService);
    }

    @Test
    @DisplayName("A：帖子不可见时评论列表按「帖子不存在」处理，不泄露下架帖的评论")
    void shouldRejectListingCommentsOfInvisiblePost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pendingPost()));

        assertThatThrownBy(() -> commentService.list(POST_ID, Viewer.anonymous()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(commentRepository);
    }

    @Test
    @DisplayName("A：点赞不属于可见帖子的评论被拒（越权写）")
    void shouldRejectLikingCommentOfInvisiblePost() {
        long strangerId = 8L;
        when(userService.requireUsableById(strangerId))
                .thenReturn(User.register("emailHash", "studentIdHash", "enc", SALT));
        when(commentRepository.findById(ROOT_ID))
                .thenReturn(Optional.of(comment(ROOT_ID, POST_ID, null, AUTHOR_ID)));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pendingPost()));

        assertThatThrownBy(() -> commentService.like(ROOT_ID, strangerId))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verify(likeRepository, never()).save(any());
    }

    @Test
    @DisplayName("B：封禁用户不能发表评论")
    void shouldRejectCommentFromBannedUser() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(visiblePost()));
        when(userService.requireUsableById(AUTHOR_ID))
                .thenThrow(new BizException(ErrorCode.USER_BANNED));

        assertThatThrownBy(() -> commentService.create(POST_ID, AUTHOR_ID, new CreateCommentRequest("评论", null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("本地硬拦截已接入评论链路，命中即拒绝且不落库")
    void shouldRejectCommentWhenModeratorBlocks() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(visiblePost()));
        when(userService.requireUsableById(AUTHOR_ID))
                .thenReturn(User.register("emailHash", "studentIdHash", "enc", SALT));
        ContentGuard blocking = text -> {
            throw new BizException(ErrorCode.CONTENT_REJECTED);
        };
        CommentService strict = new CommentService(commentRepository, postRepository, likeRepository,
                anonymousCodeService, userService, auditLogService, notificationPort, blocking);

        assertThatThrownBy(() -> strict.create(POST_ID, AUTHOR_ID, new CreateCommentRequest("评论", null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CONTENT_REJECTED);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("评论列表：一级评论带出回复，并批量装配点赞数（游客没有 liked 状态）")
    void shouldAssembleRepliesAndLikes() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(visiblePost()));
        when(commentRepository.findByPostIdAndParentIdIsNullAndStatusOrderByCreatedAtAsc(
                POST_ID, CommentStatus.PUBLISHED))
                .thenReturn(List.of(comment(ROOT_ID, POST_ID, null, AUTHOR_ID)));
        when(commentRepository.findByParentIdInAndStatusOrderByCreatedAtAsc(
                List.of(ROOT_ID), CommentStatus.PUBLISHED))
                .thenReturn(List.of(comment(REPLY_ID, POST_ID, ROOT_ID, 8L)));
        when(likeRepository.countByTargets(LikeTargetType.COMMENT, List.of(ROOT_ID, REPLY_ID)))
                .thenReturn(List.of(new LikeCount(ROOT_ID, 3L)));

        List<CommentResponse> comments = commentService.list(POST_ID, Viewer.anonymous());

        assertThat(comments).hasSize(1);
        CommentResponse root = comments.getFirst();
        assertThat(root.likeCount()).isEqualTo(3);
        assertThat(root.liked()).isFalse();
        assertThat(root.replies()).hasSize(1);
        assertThat(root.replies().getFirst().id()).isEqualTo(REPLY_ID);
        assertThat(root.replies().getFirst().likeCount()).isZero();
    }

    @Test
    @DisplayName("帖子没有评论时不查回复表，直接返回空列表")
    void shouldReturnEmptyWhenNoComments() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(visiblePost()));
        when(commentRepository.findByPostIdAndParentIdIsNullAndStatusOrderByCreatedAtAsc(
                POST_ID, CommentStatus.PUBLISHED))
                .thenReturn(List.of());

        assertThat(commentService.list(POST_ID, Viewer.anonymous())).isEmpty();
        verifyNoInteractions(likeRepository);
    }

    // ------------------------------------------------------------------ 辅助

    private static Post visiblePost() {
        return TestFixtures.approvedPost(POST_ID, AUTHOR_ID, 3L, null);
    }

    /** 未过审的帖子：除作者外一律不可见 */
    private static Post pendingPost() {
        Post pending = Post.draft(AUTHOR_ID, 3L, "待审", null);
        pending.submit(com.qinglan.post.entity.RiskLevel.LOW);
        TestFixtures.setId(pending, POST_ID);
        return pending;
    }

    private static Comment comment(long id, long postId, Long parentId, long authorId) {
        Comment comment = Comment.create(postId, authorId, parentId, "内容");
        TestFixtures.setId(comment, id);
        comment.assignAnonymousCode("青竹#0f0f");
        return comment;
    }
}
