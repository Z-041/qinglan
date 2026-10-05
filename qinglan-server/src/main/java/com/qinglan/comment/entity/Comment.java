package com.qinglan.comment.entity;

import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 二级评论：{@code parentId} 为空即一级评论，非空即对一级评论的回复。
 *
 * <p>匿名代号同样按帖子派生（{@code hash(post_id + author_id + salt)}），
 * 保证同一作者在帖子与自己的评论里代号一致、跨帖不可关联（不变量 #1）。</p>
 */
@Getter
@Entity
@Table(name = "comment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    /**
     * 插入时的占位代号，理由同 {@code Post}：真实代号按 {@code hash(post_id + author_id + salt)}
     * 派生，只能在 INSERT 拿到主键后回填，而列是 NOT NULL（不变量 #1）。
     */
    private static final String PLACEHOLDER_ANONYMOUS_CODE = "PENDING";

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommentStatus status;

    @Column(name = "anonymous_code", nullable = false, length = 32)
    private String anonymousCode;

    /**
     * @param parentId 一级评论传 null；回复时必须是同帖下的一级评论 id
     */
    public static Comment create(Long postId, Long authorId, Long parentId, String content) {
        Comment comment = new Comment();
        comment.postId = postId;
        comment.authorId = authorId;
        comment.parentId = parentId;
        comment.content = content;
        comment.status = CommentStatus.PUBLISHED;
        comment.anonymousCode = PLACEHOLDER_ANONYMOUS_CODE;
        return comment;
    }

    public void assignAnonymousCode(String anonymousCode) {
        this.anonymousCode = anonymousCode;
    }

    /** 作者自行删除或管理员移除统一走这里 */
    public void remove() {
        this.status = CommentStatus.REMOVED;
    }

    public boolean isRemoved() {
        return status == CommentStatus.REMOVED;
    }

    public boolean isAuthoredBy(Long userId) {
        return userId != null && Objects.equals(this.authorId, userId);
    }

    public boolean isReply() {
        return parentId != null;
    }
}
