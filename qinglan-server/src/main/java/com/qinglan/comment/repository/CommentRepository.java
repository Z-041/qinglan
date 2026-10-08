package com.qinglan.comment.repository;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.entity.CommentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 帖子下的一级评论，键集分页。
     *
     * <p>排序键 {@code (created_at asc, id asc)}，游标是这两列的复合值；两个比较条件必须与
     * {@code order by} 严格同序。只按时间做键集会在同一微秒并列时漏行或重复，故必须带上
     * {@code id} 段。首页传 {@code cursorCreatedAt = null}，由第一个分支恒真兜住，
     * 因此首页与后续页共用同一条查询，不必维护两份近乎相同的 JPQL。</p>
     */
    @Query("""
            select c from Comment c
            where c.postId = :postId
              and c.parentId is null
              and c.status = :status
              and (:cursorCreatedAt is null
                   or c.createdAt > :cursorCreatedAt
                   or (c.createdAt = :cursorCreatedAt and c.id > :cursorId))
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findRootPage(
            @Param("postId") Long postId,
            @Param("status") CommentStatus status,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable limit);

    /** 一批一级评论下的所有回复，按时间正序（评论树一次性取出，避免逐条查子节点） */
    List<Comment> findByParentIdInAndStatusOrderByCreatedAtAsc(List<Long> parentIds, CommentStatus status);
}
