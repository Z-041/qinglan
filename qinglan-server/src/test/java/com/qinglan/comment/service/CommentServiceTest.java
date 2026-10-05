package com.qinglan.comment.service;

import com.qinglan.comment.dto.CommentResponse;
import com.qinglan.comment.dto.CreateCommentRequest;
import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.entity.CommentStatus;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.anonymous.AnonymousCodeService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.interaction.LikeCount;
import com.qinglan.common.interaction.LikeRepository;
import com.qinglan.common.interaction.LikeTargetType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, postRepository, likeRepository,
                anonymousCodeService, userService);
    }

    @Test
    @DisplayName("发表一级评论：代号与帖子同源派生，并让 post.comment_count 加一")
    void shouldCreateRootCommentAndBumpCount() {
        Post post = visiblePost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(userService.requireById(AUTHOR_ID))
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
        assertThat(post.getCommentCount()).isEqualTo(1);
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
    @DisplayName("删除一级评论时连同其回复一并移除，并按实际条数扣减计数")
    void shouldCascadeRemoveRepliesAndDecreaseCount() {
        Post post = visiblePost();
        post.increaseCommentCount();
        post.increaseCommentCount();
        post.increaseCommentCount();
        when(commentRepository.findById(ROOT_ID))
                .thenReturn(Optional.of(comment(ROOT_ID, POST_ID, null, AUTHOR_ID)));
        when(commentRepository.findByParentIdInAndStatusOrderByCreatedAtAsc(List.of(ROOT_ID), CommentStatus.PUBLISHED))
                .thenReturn(List.of(
                        comment(REPLY_ID, POST_ID, ROOT_ID, 8L),
                        comment(REPLY_ID + 1, POST_ID, ROOT_ID, 9L)));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        commentService.delete(ROOT_ID, AUTHOR_ID);

        assertThat(post.getCommentCount()).isZero();
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
        verify(postRepository, never()).findById(any());
    }

    @Test
    @DisplayName("评论列表：一级评论带出回复，并批量装配点赞数（游客没有 liked 状态）")
    void shouldAssembleRepliesAndLikes() {
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

    private static Comment comment(long id, long postId, Long parentId, long authorId) {
        Comment comment = Comment.create(postId, authorId, parentId, "内容");
        TestFixtures.setId(comment, id);
        comment.assignAnonymousCode("青竹#0f0f");
        return comment;
    }
}
