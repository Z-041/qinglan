package com.qinglan.common.audit;

import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.Collection;

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
}
