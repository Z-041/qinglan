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
import com.qinglan.common.interaction.LikeRecord;
import com.qinglan.common.interaction.LikeRepository;
import com.qinglan.common.interaction.LikeStateResponse;
import com.qinglan.common.interaction.LikeTargetType;
import com.qinglan.common.moderation.ContentGuard;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.security.Viewer;
import com.qinglan.post.entity.Post;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.user.entity.User;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 二级评论。
 *
 * <p>评论走「先发后审」（设计方案第一章红线 2），因此不参与主帖那套审核状态机，
 * 只有 {@code PUBLISHED / REMOVED} 两态；遇违规由管理员直接移除或由举报驱动。</p>
 *
 * <p>跨模块依赖：{@code comment → post}，用于校验帖子可见性与维护 {@code post.comment_count}
 * （AGENTS.md 第三章白名单，冗余计数是为了让信息流不必逐帖 count）。</p>
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    /** 通知正文（评论摘要）截断长度；超出补省略号，避免把整条评论塞进通知 */
    private static final int NOTIFICATION_BODY_LENGTH = 50;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final AnonymousCodeService anonymousCodeService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final NotificationPort notificationPort;
    private final ContentGuard contentGuard;

    /**
     * 评论列表：一次取出该帖全部一级评论与其回复，再批量装配点赞，避免评论树的 N+1。
     *
     * <p>先校验帖子可见性：否则可以绕过帖子详情、直接读已下架帖子的评论。</p>
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long postId, Viewer viewer) {
        requireVisiblePost(postId, viewer.userId());
        List<Comment> roots = commentRepository
                .findByPostIdAndParentIdIsNullAndStatusOrderByCreatedAtAsc(postId, CommentStatus.PUBLISHED);
        if (roots.isEmpty()) {
            return List.of();
        }
        List<Long> rootIds = roots.stream().map(Comment::getId).toList();
        List<Comment> replies = commentRepository
                .findByParentIdInAndStatusOrderByCreatedAtAsc(rootIds, CommentStatus.PUBLISHED);

        List<Long> allIds = new ArrayList<>(rootIds);
        replies.forEach(reply -> allIds.add(reply.getId()));
        Map<Long, Long> likeCounts = likeRepository.countByTargets(LikeTargetType.COMMENT, allIds).stream()
                .collect(Collectors.toMap(LikeCount::targetId, LikeCount::count));
        Set<Long> likedIds = viewer.userId() == null
                ? Set.of()
                : Set.copyOf(likeRepository.findLikedTargetIds(LikeTargetType.COMMENT, viewer.userId(), allIds));

        Map<Long, List<CommentResponse>> repliesByParent = new LinkedHashMap<>();
        for (Comment reply : replies) {
            repliesByParent.computeIfAbsent(reply.getParentId(), key -> new ArrayList<>())
                    .add(toResponse(reply, List.of(), likeCounts, likedIds, viewer));
        }
        return roots.stream()
                .map(root -> toResponse(root, repliesByParent.getOrDefault(root.getId(), List.of()),
                        likeCounts, likedIds, viewer))
                .toList();
    }

    @Transactional
    public CommentResponse create(Long postId, Long authorId, CreateCommentRequest request) {
        Post post = requireVisiblePost(postId, authorId);
        Long parentId = request.parentId();
        Comment parent = parentId == null ? null : requireReplyableRoot(parentId, postId);
        User author = userService.requireUsableById(authorId);
        requireAllowedContent(request.content());

        Comment comment = Comment.create(postId, authorId, parentId, request.content().trim());
        Comment saved = commentRepository.save(comment);
        // 与主帖同源派生：同一作者在同一帖下的评论代号与帖子代号一致（不变量 #1）
        saved.assignAnonymousCode(
                anonymousCodeService.deriveCode(postId, authorId, author.getAnonymousSalt()));
        // 原子自增：读-改-写会在并发下丢更新（C）
        postRepository.adjustCommentCount(postId, 1);
        // 通知帖主。body 只能是评论正文，绝不带上评论者的匿名代号 / authorId，否则跨帖可关联（不变量 #1）
        notifyComment(post.getAuthorId(), authorId, "有人评论了你的帖子", request.content().trim(), postId);
        // 二级评论还要通知**被回复的人**：只通知帖主的话，被回复者永远不知道自己被回复了
        // （同一条评论若两人是同一人则只发一条，避免重复打扰）
        if (parent != null && !parent.getAuthorId().equals(post.getAuthorId())) {
            notifyComment(parent.getAuthorId(), authorId, "有人回复了你的评论", request.content().trim(), postId);
        }

        log.info("用户发表评论 commentId={} postId={} parentId={}", saved.getId(), postId, parentId);
        return toResponse(saved, List.of(), Map.of(), Set.of(), new Viewer(authorId));
    }

    /** 发一条评论类通知；正文按列长截断。绝不带上评论者身份（不变量 #1） */
    private void notifyComment(Long recipientId, Long actorId, String title, String content, Long postId) {
        notificationPort.notify(recipientId, actorId,
                new NotificationMessage(NotificationType.COMMENT, title,
                        truncateForNotification(content), "/posts/" + postId));
    }

    /**
     * 删除评论（仅作者本人）。
     *
     * <p>删除一级评论时，其下的回复会一并移除并同步扣减计数——否则列表里挂着的回复仍在库里
     * 标着 PUBLISHED，{@code post.comment_count} 也会与可见评论数长期对不上。</p>
     *
     * <p>评论删除没有恢复入口，所以被移除的评论（含连带移除的回复）的点赞行在**同一事务内硬删**，
     * 不留再也读不到、也不会被回收的孤儿行（R-15）。帖子侧不同：下架可申诉恢复，点赞行不能这样删。</p>
     */
    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isAuthoredBy(userId)) {
            throw new BizException(ErrorCode.NOT_CONTENT_OWNER);
        }
        if (comment.isRemoved()) {
            return;
        }
        int removed = removeWithReplies(comment);
        log.info("删除评论 commentId={} 连同回复共 {} 条", commentId, removed);
    }

    /**
     * 管理员移除评论（先发后审的唯一兜底处置）。
     *
     * <p>移除细节与作者自删完全共用 {@link #removeWithReplies}——硬删点赞行这类步骤漏一处，
     * 就会留下读不到的孤儿数据。处置动作必须留痕（不变量 #7），故这里写审计日志。</p>
     */
    @Transactional
    public void removeByAdmin(Long commentId, Long operatorId, String reason) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
        if (comment.isRemoved()) {
            return;
        }
        int removed = removeWithReplies(comment);
        auditLogService.record(AuditTargetType.COMMENT, commentId, AuditAction.COMMENT_REMOVE, operatorId, reason);
        log.info("管理员移除评论 commentId={} 连同回复共 {} 条，操作人 operatorId={}", commentId, removed, operatorId);
    }

    /** 给评论点赞，幂等 */
    @Transactional
    public LikeStateResponse like(Long commentId, Long userId) {
        requireVisibleComment(commentId, userId);
        if (likeRepository.findByTargetTypeAndTargetIdAndUserId(
                LikeTargetType.COMMENT, commentId, userId).isEmpty()) {
            likeRepository.save(LikeRecord.of(LikeTargetType.COMMENT, commentId, userId));
        }
        return likeState(commentId, userId);
    }

    /** 取消评论点赞，幂等 */
    @Transactional
    public LikeStateResponse unlike(Long commentId, Long userId) {
        requireVisibleComment(commentId, userId);
        likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.COMMENT, commentId, userId)
                .ifPresent(likeRepository::delete);
        return likeState(commentId, userId);
    }

    // ---------------------------------------------------------------- 内部方法

    /**
     * 软删评论本身与（一级评论的）全部回复，同步扣减 {@code post.comment_count}，
     * 并在同一事务内硬删这些评论的点赞行——评论删除没有恢复入口（R-15）。
     *
     * @return 实际移除的评论条数（含连带移除的回复）
     */
    private int removeWithReplies(Comment comment) {
        comment.remove();
        List<Long> removedIds = new ArrayList<>();
        removedIds.add(comment.getId());
        if (!comment.isReply()) {
            List<Comment> replies = commentRepository
                    .findByParentIdInAndStatusOrderByCreatedAtAsc(List.of(comment.getId()), CommentStatus.PUBLISHED);
            for (Comment reply : replies) {
                reply.remove();
                removedIds.add(reply.getId());
            }
        }
        // 原子自减：读-改-写在并发删除下会丢更新
        postRepository.adjustCommentCount(comment.getPostId(), -removedIds.size());
        likeRepository.deleteByTargets(LikeTargetType.COMMENT, removedIds);
        return removedIds.size();
    }

    private Post requireVisiblePost(Long postId, Long viewerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.canBeViewedBy(viewerId)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    /**
     * 校验被回复的父评论可回复，并返回它——调用方要拿父评论作者去通知被回复的人。
     *
     * <p>只支持两级：回复必须落在同一帖的一级评论下，防止构造任意深度的评论树。</p>
     */
    private Comment requireReplyableRoot(Long parentId, Long postId) {
        Comment parent = commentRepository.findById(parentId)
                .filter(candidate -> !candidate.isRemoved())
                .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
        if (!parent.getPostId().equals(postId) || parent.isReply()) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return parent;
    }

    /**
     * 点赞/取消点赞的前置校验：账号可用、评论存在且未移除、且其所属帖子对当前用户可见。
     * 缺了最后一条，已下架帖子的评论仍可被点赞（越权写）。
     */
    private void requireVisibleComment(Long commentId, Long userId) {
        userService.requireUsableById(userId);
        Comment comment = commentRepository.findById(commentId)
                .filter(candidate -> !candidate.isRemoved())
                .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
        requireVisiblePost(comment.getPostId(), userId);
    }

    /** 本地硬拦截（P2）：敏感词与隐私格式在入库前拦下，命中即抛，不进入待审流程 */
    private void requireAllowedContent(String content) {
        contentGuard.check(content);
    }

    /** 评论摘要：超过 {@value #NOTIFICATION_BODY_LENGTH} 字截断并补省略号，与既有预览截断同风格 */
    private static String truncateForNotification(String content) {
        return content.length() > NOTIFICATION_BODY_LENGTH
                ? content.substring(0, NOTIFICATION_BODY_LENGTH) + "..."
                : content;
    }

    private LikeStateResponse likeState(Long commentId, Long userId) {
        long count = likeRepository.countByTargetTypeAndTargetId(LikeTargetType.COMMENT, commentId);
        boolean liked = likeRepository
                .findByTargetTypeAndTargetIdAndUserId(LikeTargetType.COMMENT, commentId, userId).isPresent();
        return new LikeStateResponse(count, liked);
    }

    private CommentResponse toResponse(Comment comment, List<CommentResponse> replies,
                                       Map<Long, Long> likeCounts, Set<Long> likedIds, Viewer viewer) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAnonymousCode(),
                likeCounts.getOrDefault(comment.getId(), 0L),
                likedIds.contains(comment.getId()),
                viewer.is(comment.getAuthorId()),
                comment.getCreatedAt(),
                replies);
    }
}
