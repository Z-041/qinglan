package com.qinglan.announcement.repository;

import com.qinglan.announcement.entity.Announcement;
import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.announcement.entity.AnnouncementStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /**
     * 待读一条：取「生效中且未被该 viewer 读过」的公告，按级别优先级（URGENT 最高）再按发布时间倒序。
     *
     * <p>用 {@code not exists} 一次排除已读。两个参数都是「排除维度」且**由调用方决定只传其一**：
     * 登录用户只按 {@code userId} 排除、游客只按 {@code deviceId} 排除——若两个都传，共用设备上
     * 他人的游客已读记录会把登录用户也挡掉（漏看强制阅读公告比多看一次危险）。两侧逻辑写在
     * {@code AnnouncementService#findPending}。</p>
     *
     * <p>{@code levels} 由调用方决定要哪几级：弹窗用「全部级别」（未读即弹），
     * 发帖 gate 只取 {@code URGENT}（设计方案 5.4「是否启用此拦截由 level=URGENT 决定，
     * 普通公告不拦截」）。两者共用一条查询，避免两份近乎相同的 JPQL 各自漂移。</p>
     *
     * <p>排序用显式 {@code case level when ... then ...}：枚举声明顺序即优先级顺序，但数据库里存的是名称字符串，
     * 直接按列排序会变成字母序（ACTIVITY &lt; NORMAL &lt; URGENT），与优先级相反，故必须显式指定。
     * 调用方传 {@code PageRequest.of(0, 1)} 取优先级最高的一条。</p>
     */
    @Query("""
            select a from Announcement a
            where a.status = :published
              and a.level in :levels
              and a.startAt <= :now
              and (a.endAt is null or a.endAt > :now)
              and not exists (
                  select 1 from AnnouncementRead r
                  where r.announcementId = a.id
                    and ((:userId is not null and r.userId = :userId)
                         or (:deviceId is not null and r.deviceId = :deviceId))
              )
            order by case a.level when :urgent then 0 when :normal then 1 else 2 end,
                     a.publishedAt desc
            """)
    List<Announcement> findPending(
            @Param("published") AnnouncementStatus published,
            @Param("levels") Collection<AnnouncementLevel> levels,
            @Param("urgent") AnnouncementLevel urgent,
            @Param("normal") AnnouncementLevel normal,
            @Param("now") Instant now,
            @Param("userId") Long userId,
            @Param("deviceId") String deviceId,
            Pageable limit);

    /**
     * 学生端公告历史：已发布公告，按 id 倒序游标翻页。
     * 首页游标传 {@link Long#MAX_VALUE}，省掉 SQL 里的 null 分支。
     */
    @Query("""
            select a from Announcement a
            where a.status = :published and a.id < :cursor
            order by a.id desc
            """)
    List<Announcement> findHistory(
            @Param("published") AnnouncementStatus published,
            @Param("cursor") Long cursor,
            Pageable limit);

    /** 管理端列表：全状态、按 id 倒序键集分页 */
    @Query("""
            select a from Announcement a
            where a.id < :cursor
            order by a.id desc
            """)
    List<Announcement> findAdminPage(@Param("cursor") Long cursor, Pageable limit);

    /** 当日发布数：用于发布频率上限（计划 D9） */
    long countByStatusAndPublishedAtGreaterThanEqual(AnnouncementStatus status, Instant from);
}
