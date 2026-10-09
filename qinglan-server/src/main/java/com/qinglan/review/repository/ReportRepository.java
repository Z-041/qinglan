package com.qinglan.review.repository;

import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
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
     * 审核队列（默认排序）：按目标聚合待处理举报，举报数降序、首报时间升序（等得久的先处理）。
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
     * <p><b>筛选进 SQL、游标仍是键集</b>（管理端阶段 A）：{@code reasons} 与 {@code targetType}
     * 过滤 {@code where}，作用在「先筛举报、再按目标聚合」上——因此 {@code reportCount} 是**命中筛选
     * 的举报数**，与游标里的这个数一致，翻页不会漏行。{@code overdueBefore} 是聚合结果的超时判定
     * （首报早于该时刻），只能放 {@code having}：若下推到 {@code where} 会把聚合的 {@code count} 一并
     * 缩小，让「举报数」这一列失真。调用方在无筛选时把 {@code reasons} 填成全部枚举值（等价于不过滤），
     * 以避免空集合的 {@code in} 在不同数据库上的行为差异。</p>
     *
     * <p>聚合在数据库完成，不把全表举报捞进内存——AGENTS.md 安全红线禁止无限制全表扫描。
     * 必须给 {@code Pageable}：一份待处理举报最多对应一行，行数有界。</p>
     */
    @Query("""
            select new com.qinglan.review.repository.ReviewQueueRow(
                r.targetType, r.targetId, count(r), min(r.createdAt))
            from Report r
            where r.status = :status
              and r.reasonCode in :reasons
              and (:targetType is null or r.targetType = :targetType)
            group by r.targetType, r.targetId
            having (:overdueBefore is null or min(r.createdAt) < :overdueBefore)
              and ((count(r) < :cursorReportCount)
                or (count(r) = :cursorReportCount and min(r.createdAt) > :cursorFirstReportedAt)
                or (count(r) = :cursorReportCount and min(r.createdAt) = :cursorFirstReportedAt
                    and r.targetId < :cursorTargetId)
                or (count(r) = :cursorReportCount and min(r.createdAt) = :cursorFirstReportedAt
                    and r.targetId = :cursorTargetId
                    and case when r.targetType = com.qinglan.review.entity.ReviewTargetType.POST
                             then 0 else 1 end < :cursorTypeRank))
            order by count(r) desc, min(r.createdAt) asc, r.targetId desc,
                case when r.targetType = com.qinglan.review.entity.ReviewTargetType.POST
                     then 0 else 1 end desc
            """)
    List<ReviewQueueRow> findQueueAggregates(
            @Param("status") ReportStatus status,
            @Param("reasons") Collection<ReportReason> reasons,
            @Param("targetType") ReviewTargetType targetType,
            @Param("overdueBefore") Instant overdueBefore,
            @Param("cursorReportCount") long cursorReportCount,
            @Param("cursorFirstReportedAt") Instant cursorFirstReportedAt,
            @Param("cursorTargetId") long cursorTargetId,
            @Param("cursorTypeRank") int cursorTypeRank,
            Pageable limit);

    /**
     * 审核队列（首报时间升序排序）：超时优先场景下按「等得最久先处理」排列。
     *
     * <p>排序键为三元组 {@code (min(首报时间) asc, 目标 id desc, 目标类型序 desc)}，游标是与之同序的
     * 三元组——与默认排序的四元组游标**格式不同**，因此解析游标时必须按 {@code sort} 决定段数，
     * 否则会漏行或重复。并列行（同首报时间）的翻页由后两个键保证不重不漏。</p>
     *
     * <p>筛选与超时判定的口径同 {@link #findQueueAggregates}。</p>
     */
    @Query("""
            select new com.qinglan.review.repository.ReviewQueueRow(
                r.targetType, r.targetId, count(r), min(r.createdAt))
            from Report r
            where r.status = :status
              and r.reasonCode in :reasons
              and (:targetType is null or r.targetType = :targetType)
            group by r.targetType, r.targetId
            having (:overdueBefore is null or min(r.createdAt) < :overdueBefore)
              and ((min(r.createdAt) > :cursorFirstReportedAt)
                or (min(r.createdAt) = :cursorFirstReportedAt and r.targetId < :cursorTargetId)
                or (min(r.createdAt) = :cursorFirstReportedAt and r.targetId = :cursorTargetId
                    and case when r.targetType = com.qinglan.review.entity.ReviewTargetType.POST
                             then 0 else 1 end < :cursorTypeRank))
            order by min(r.createdAt) asc, r.targetId desc,
                case when r.targetType = com.qinglan.review.entity.ReviewTargetType.POST
                     then 0 else 1 end desc
            """)
    List<ReviewQueueRow> findQueueAggregatesByFirstReportedAsc(
            @Param("status") ReportStatus status,
            @Param("reasons") Collection<ReportReason> reasons,
            @Param("targetType") ReviewTargetType targetType,
            @Param("overdueBefore") Instant overdueBefore,
            @Param("cursorFirstReportedAt") Instant cursorFirstReportedAt,
            @Param("cursorTargetId") long cursorTargetId,
            @Param("cursorTypeRank") int cursorTypeRank,
            Pageable limit);

    /**
     * 待处理举报的**目标数**（与审核队列行数一致）：按目标类型分别对 {@code target_id} 去重后相加。
     *
     * <p>{@code target_id} 只在各自类型内唯一，故不能一次性对 {@code target_id} 去重——帖子 #7 与
     * 评论 #7 会被错并成一行。分两次（POST / COMMENT）各自去重再求和才等于队列行数。</p>
     */
    @Query("""
            select count(distinct r.targetId) from Report r
            where r.status = :status and r.targetType = :targetType
            """)
    long countDistinctTargets(
            @Param("status") ReportStatus status, @Param("targetType") ReviewTargetType targetType);

    /** 举报按理由分组的计数（看板「举报按理由分布」），一条查询出全部理由，行数受枚举基数约束 */
    @Query("""
            select new com.qinglan.review.repository.ReportReasonCount(r.reasonCode, count(r))
            from Report r
            group by r.reasonCode
            """)
    List<ReportReasonCount> countGroupedByReason();

    /**
     * 已处置举报的提交 / 处置时刻，用于计算处置时效（P50 / P90）。
     *
     * <p>窗口由调用方以「处置时刻 ≥ from」限定（近 30 天），投影只有两列，避免把整行拉出来。</p>
     */
    @Query("""
            select new com.qinglan.review.repository.ReportHandlingTime(r.createdAt, r.handledAt)
            from Report r
            where r.status <> com.qinglan.review.entity.ReportStatus.PENDING
              and r.handledAt >= :from
            """)
    List<ReportHandlingTime> findHandledTimes(@Param("from") Instant from);

    /**
     * 某目标的全部举报人 id（去重、不限举报状态）。
     *
     * <p><b>只用于聚合计数，绝不把名单返回给前端</b>——举报人身份不出审核队列
     * （不变量 #1 的引申，与 {@code ReviewQueueItemResponse} 的约束同源）。</p>
     */
    @Query("""
            select distinct r.reporterId from Report r
            where r.targetType = :targetType and r.targetId = :targetId
            """)
    List<Long> findDistinctReporterIds(
            @Param("targetType") ReviewTargetType targetType, @Param("targetId") Long targetId);

    /**
     * 给定举报人中，除本目标外还举报过其他目标的人数。
     *
     * <p>等价于「举报过 ≥2 个目标的人数」：本目标算一个，另有任意其他目标即满足。</p>
     */
    @Query("""
            select count(distinct r.reporterId) from Report r
            where r.reporterId in :reporterIds
              and (r.targetType <> :targetType or r.targetId <> :targetId)
            """)
    long countReportersWithOtherReports(
            @Param("reporterIds") Collection<Long> reporterIds,
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId);

    long countByStatus(ReportStatus status);
}
