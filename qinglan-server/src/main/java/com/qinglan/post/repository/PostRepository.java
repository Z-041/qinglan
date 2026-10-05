package com.qinglan.post.repository;

import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
