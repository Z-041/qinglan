package com.qinglan.comment.repository;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.entity.CommentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 帖子下的一级评论，按时间正序 */
    List<Comment> findByPostIdAndParentIdIsNullAndStatusOrderByCreatedAtAsc(Long postId, CommentStatus status);

    /** 一批一级评论下的所有回复，按时间正序（评论树一次性取出，避免逐条查子节点） */
    List<Comment> findByParentIdInAndStatusOrderByCreatedAtAsc(List<Long> parentIds, CommentStatus status);
}
