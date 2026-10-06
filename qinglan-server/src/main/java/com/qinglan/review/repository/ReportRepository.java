package com.qinglan.review.repository;

import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 判断某用户是否已举报过该目标：业务层用它把重复举报映射成 409（不变量 #4）。
     * 并发下仍以 {@code uk_report_target_reporter} 为最终防线。
     */
    Optional<Report> findByTargetTypeAndTargetIdAndReporterId(
            ReviewTargetType targetType, Long targetId, Long reporterId);

    /** 某目标下的待处理举报，处置时一次性取出结案 */
    List<Report> findByTargetTypeAndTargetIdAndStatus(
            ReviewTargetType targetType, Long targetId, ReportStatus status);

    /** 一页队列目标的全部待处理举报，用于批量装配举报理由，避免逐行查询 */
    List<Report> findByTargetTypeAndTargetIdInAndStatus(
            ReviewTargetType targetType, Collection<Long> targetIds, ReportStatus status);

    /**
     * 审核队列：按目标聚合待处理举报，举报数降序、首报时间升序（等得久的先处理）。
     *
     * <p>游标是四元组 {@code (举报数, 首报时间, 目标 id, 目标类型序)} 的键集分页。四个比较条件
     * 必须与 {@code order by} 完全同序，否则会有行被跳过或重复——改排序务必同步改 {@code having}。
     * 首页由调用方传哨兵值（举报数 {@code Long.MAX_VALUE}、首报时间 {@code Instant.EPOCH}、
     * 目标 id {@code Long.MAX_VALUE}、类型序 {@code Integer.MAX_VALUE}），第一个条件恒真，
     * 借此省掉一套「首页专用」查询。</p>
     *
     * <p><b>为什么游标要带目标类型</b>：{@code target_id} 只在各自类型内唯一，帖子 #7 与评论 #7
     * 会撞号。若两者举报数与首报时间又恰好并列，仅凭 {@code target_id} 无法区分，翻页时会漏掉
     * 或重复其中之一。类型序由 {@code case} 显式算出（POST=0、COMMENT=1），而不是直接比枚举列：
     * 枚举比大小依赖映射细节，用整数可读且不随方言变。新增目标类型时必须在此补一档。</p>
     *
     * <p>{@code cursorTypeRank} 是 {@code int} 而非 {@code long}：{@code case} 的 {@code 0/1}
     * 字面量被 Hibernate 推断为 Integer，传 Long 会以 {@code InvalidDataAccessApiUsage} 失败。</p>
     *
     * <p>聚合在数据库完成，不把全表举报捞进内存——AGENTS.md 安全红线禁止无限制全表扫描。
     * 必须给 {@code Pageable}：一份待处理举报最多对应一行，行数有界。</p>
     */
    @Query("""
            select new com.qinglan.review.repository.ReviewQueueRow(
                r.targetType, r.targetId, count(r), min(r.createdAt))
            from Report r
            where r.status = :status
            group by r.targetType, r.targetId
            having (count(r) < :cursorReportCount)
                or (count(r) = :cursorReportCount and min(r.createdAt) > :cursorFirstReportedAt)
                or (count(r) = :cursorReportCount and min(r.createdAt) = :cursorFirstReportedAt
                    and r.targetId < :cursorTargetId)
                or (count(r) = :cursorReportCount and min(r.createdAt) = :cursorFirstReportedAt
                    and r.targetId = :cursorTargetId
                    and case when r.targetType = com.qinglan.review.entity.ReviewTargetType.POST
                             then 0 else 1 end < :cursorTypeRank)
            order by count(r) desc, min(r.createdAt) asc, r.targetId desc,
                case when r.targetType = com.qinglan.review.entity.ReviewTargetType.POST
                     then 0 else 1 end desc
            """)
    List<ReviewQueueRow> findQueueAggregates(
            @Param("status") ReportStatus status,
            @Param("cursorReportCount") long cursorReportCount,
            @Param("cursorFirstReportedAt") Instant cursorFirstReportedAt,
            @Param("cursorTargetId") long cursorTargetId,
            @Param("cursorTypeRank") int cursorTypeRank,
            Pageable limit);
}
