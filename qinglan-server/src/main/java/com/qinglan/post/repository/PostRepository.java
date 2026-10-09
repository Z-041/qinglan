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
     * 待审帖子的键集分页查询，供管理端「待审帖子」队列（先审后发，设计方案红线 2）。
     *
     * <p>排序键 {@code (created_at asc, id asc)}——发布即提交审核，{@code created_at} 就是
     * 提交时刻，先到先审。两个比较条件必须与 {@code order by} 严格同序，否则翻页会漏行或重复。</p>
     *
     * <p>状态取集合而非单值：与看板的「待审核」口径一致（{@code PENDING} + {@code REVIEWING}），
     * 免得将来出现认领动作后「看板数字」与「队列条数」对不上。</p>
     */
    @Query("""
            select p from Post p
            where p.status in :statuses
              and (:cursorCreatedAt is null
                   or p.createdAt > :cursorCreatedAt
                   or (p.createdAt = :cursorCreatedAt and p.id > :cursorId))
            order by p.createdAt asc, p.id asc
            """)
    List<Post> findPendingPage(
            @Param("statuses") Collection<PostStatus> statuses,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
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

    /**
     * 「我的帖子」/ 「作者最近动态」：某作者所发帖子的键集分页，按发布时间倒序（新帖在前）。
     *
     * <p>排序键 {@code (created_at desc, id desc)}，两个比较条件必须与 {@code order by} 严格同序。
     * 首页传 {@code cursorCreatedAt = null}（第一个分支恒真），与后续页共用同一条 JPQL。</p>
     *
     * <p>{@code status} 为空表示不筛选状态——「我的帖子」要覆盖待审 / 已驳回 / 已下架 / 已通过全部
     * 状态（作者能看到自己的全部帖子），而上下文面板的「作者最近动态」也不筛选。</p>
     */
    @Query("""
            select p from Post p
            where p.authorId = :authorId
              and (:status is null or p.status = :status)
              and (:cursorCreatedAt is null
                   or p.createdAt < :cursorCreatedAt
                   or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findMinePage(
            @Param("authorId") Long authorId,
            @Param("status") PostStatus status,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable limit);

    /**
     * 热门排序候选集：窗口内（默认近 7 天）审核通过的帖子，按发布时间倒序取有界的前若干条。
     *
     * <p>为什么先取候选再在内存算分：热度分含时间衰减 {@code e^(-年龄/72h)}，无法用稳定 SQL 游标
     * 表达（见 {@code PostService#listHot}）。用时间窗口 + 有界条数把候选压住，避免全表算分；
     * 排序交给内存中的复合游标。之所以自定义条数上限而非无界，是为了遵守安全红线「禁止无限制扫描」。</p>
     */
    @Query("""
            select p from Post p
            where p.status = :status
              and p.createdAt >= :windowStart
              and (:topicId is null or p.topicId = :topicId)
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findHotCandidates(
            @Param("status") PostStatus status,
            @Param("topicId") Long topicId,
            @Param("windowStart") Instant windowStart,
            Pageable limit);

    long countByStatus(PostStatus status);

    long countByStatusIn(Collection<PostStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);
}
