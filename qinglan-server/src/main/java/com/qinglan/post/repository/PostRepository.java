package com.qinglan.post.repository;

import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 首页信息流：只出审核通过的帖子，按 id 倒序游标翻页。
     *
     * <p>调用方把首页的 cursor 传 {@link Long#MAX_VALUE}，省掉 SQL 里的 null 分支。</p>
     */
    @Query("""
            select p from Post p
            where p.status = :status and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findFeed(@Param("status") PostStatus status, @Param("cursor") Long cursor, Pageable limit);

    /** 按话题过滤的信息流，同上 */
    @Query("""
            select p from Post p
            where p.status = :status and p.topicId = :topicId and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findFeedByTopic(
            @Param("status") PostStatus status,
            @Param("topicId") Long topicId,
            @Param("cursor") Long cursor,
            Pageable limit
    );

    /**
     * 原子调整 {@code comment_count}。
     *
     * <p>由 comment 模块在发表/删除评论时调用。不能走实体读-改-写：并发评论时两个事务各自
     * 读到旧值再写回，会丢更新。{@code case when} 兜住计数不低于 0，与删除时的连带移除对齐。</p>
     *
     * <p>{@code clearAutomatically} 是必需的：批量 UPDATE 绕过持久化上下文，同一个事务里
     * 随后再读帖子（如详情接口）会命中一级缓存拿到旧值。</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p
            set p.commentCount = case when p.commentCount + :delta < 0 then 0 else p.commentCount + :delta end
            where p.id = :postId
            """)
    int adjustCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    /**
     * 原子调整 {@code report_count}（待处理举报数）。
     *
     * <p>由 review 模块在提交举报（+1）与处置举报（归零）时调用，理由同
     * {@link #adjustCommentCount}：实体读-改-写在并发举报下必丢更新。{@code case when}
     * 兜住计数不低于 0。</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p
            set p.reportCount = case when p.reportCount + :delta < 0 then 0 else p.reportCount + :delta end
            where p.id = :postId
            """)
    int adjustReportCount(@Param("postId") Long postId, @Param("delta") int delta);

    long countByStatus(PostStatus status);

    long countByStatusIn(Collection<PostStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);
}
