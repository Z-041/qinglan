package com.qinglan.common.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 审计日志仓储。
 *
 * <p>不变量 #7：刻意继承最小化的 {@link Repository} 而非 JpaRepository，
 * 使编译期就不存在 delete/update 入口，从根上杜绝审计日志被改写。</p>
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    /**
     * 指定时间窗内、指定动作集合的审计条数，用于「今日处置数」与「近 7 日每日处置数」。
     *
     * <p>必须按动作过滤：不过滤的话，普通用户每查看一次联系方式（{@code CONTACT_VIEW}）都会计入，
     * 而该动作由浏览行为触发、量级远高于真正的处置，会让「处置数」这一列彻底失真。</p>
     *
     * <p>只加读方法，不引入 delete / update 入口，不变量 #7 不受影响。</p>
     */
    long countByActionInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Collection<AuditAction> actions, Instant from, Instant to);

    /**
     * 审计日志查询（P5 设计 7.5）：键集分页 + 可选动作 / 时间筛选。
     *
     * <p>排序键 {@code (created_at desc, id desc)}，游标是这两列的复合值；比较条件必须与
     * {@code order by} 严格同序。首页传 {@code cursorCreatedAt = null}，由第一个分支恒真兜住，
     * 因此首页与后续页共用一条 JPQL。</p>
     *
     * <p>只读方法与 {@link #save} 一样不破坏「只增不改删」。</p>
     */
    @Query("""
            select l from AuditLog l
            where (:action is null or l.action = :action)
              and (:from is null or l.createdAt >= :from)
              and (:to is null or l.createdAt < :to)
              and (:cursorCreatedAt is null
                   or l.createdAt < :cursorCreatedAt
                   or (l.createdAt = :cursorCreatedAt and l.id < :cursorId))
            order by l.createdAt desc, l.id desc
            """)
    List<AuditLog> findAdminPage(
            @Param("action") AuditAction action,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable limit);
}
