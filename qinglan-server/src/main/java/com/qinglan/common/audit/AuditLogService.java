package com.qinglan.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志写入。所有下架、封禁、公告发布等管理动作必须调用本服务留痕（不变量 #7）。
 *
 * <p>与调用方同事务同步落库，不做异步：审计若可能丢，等于没有留痕。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /**
     * 系统自动处置（如举报数达阈值自动下架）的伪操作人。
     *
     * <p>{@code user.id} 自增从 1 起，0 不会与真实用户冲突；审计表的 {@code operator_id}
     * 是 NOT NULL，需要一个显式哨兵值来表示「不是人点的」，而不是留 null 后靠猜。</p>
     */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog record(AuditTargetType targetType, Long targetId, AuditAction action, Long operatorId, String reason) {
        return auditLogRepository.save(new AuditLog(targetType, targetId, action, operatorId, reason));
    }
}
