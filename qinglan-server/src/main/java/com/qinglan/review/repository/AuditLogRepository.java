package com.qinglan.review.repository;

import com.qinglan.review.entity.AuditLog;
import org.springframework.data.repository.Repository;

/**
 * 审计日志仓储。
 *
 * <p>不变量 #7：刻意继承最小化的 {@link Repository} 而非 JpaRepository，
 * 使编译期就不存在 delete/update 入口，从根上杜绝审计日志被改写。</p>
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);
}
