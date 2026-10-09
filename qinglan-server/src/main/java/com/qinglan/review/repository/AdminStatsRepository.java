package com.qinglan.review.repository;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 看板的审计聚合查询（阶段 C，消化后端缺陷 M6）。
 *
 * <p>与 {@code ReviewContextRepository} 同一取向：跨表聚合放在 {@code review} 模块（白名单里
 * 唯一能同时读 {@code post}/{@code comment}/{@code user} 的模块），并沿用审计仓储「只读不写」的
 * 最小接口约定——不变量 #7 不受影响。</p>
 */
public interface AdminStatsRepository extends Repository<AuditLog, Long> {

    /**
     * 时间窗内按 **Asia/Shanghai 自然日** 分组的处置数（M6：把原来逐日 7 次 COUNT 合并为一条
     * GROUP BY）。
     *
     * <p>为什么用原生 SQL：日界线换算需要把 UTC 存储的 {@code created_at} 平移到东八区再取日期，
     * JPQL 无法表达 {@code interval} 运算。{@code Asia/Shanghai} 自 1991 年起恒为 UTC+8 且无夏令时，
     * 故用固定的 {@code + interval 8 hour}。<b>这是全项目唯一一处「时区换算进 SQL」的地方</b>——
     * Service 的 {@code STATS_ZONE} 与这里的 8 小时必须同义，改一处就要改另一处（有跨日边界测试兜底）。</p>
     *
     * <p>{@code date_format(...)} 返回字符串，直接以 {@code LocalDate.parse} 解析，避免
     * {@code DATE} 列在不同驱动下映射成 {@code java.sql.Date} / {@code LocalDate} 的不确定。</p>
     *
     * <p>{@code action in (:actions)} 传动作名集合；{@code created_at} 上有
     * {@code idx_review_log_created}，范围扫描后分组，行数受窗口约束。</p>
     */
    @Query(value = """
            select date_format(created_at + interval 8 hour, '%Y-%m-%d') as day, count(*) as total
            from review_log
            where created_at >= :from and created_at < :to
              and action in (:actions)
            group by day
            """, nativeQuery = true)
    List<Object[]> countHandledByDay(
            @Param("actions") Collection<String> actions,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * 审核员处置量排行：按 {@code operator_id} 分组计数，降序；只回 operatorId 与计数。
     *
     * <p>排除 {@code operator_id = 0}（系统自动处置的哨兵值）——那不是「审核员」，计入会让榜首
     * 变成一个不存在的账号。命中 {@code idx_review_log_created} 后分组；{@code Pageable} 限制条数。</p>
     */
    @Query("""
            select l.operatorId, count(l) from AuditLog l
            where l.action in :actions and l.operatorId <> 0
            group by l.operatorId
            order by count(l) desc, l.operatorId asc
            """)
    List<Object[]> countHandledByOperator(
            @Param("actions") Collection<AuditAction> actions, Pageable limit);
}
