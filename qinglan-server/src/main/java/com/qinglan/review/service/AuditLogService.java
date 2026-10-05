package com.qinglan.review.service;

import com.qinglan.review.entity.AuditAction;
import com.qinglan.review.entity.AuditLog;
import com.qinglan.review.entity.AuditTargetType;
import com.qinglan.review.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志写入。所有下架、封禁、公告发布等管理动作必须调用本服务留痕（不变量 #7）。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog record(AuditTargetType targetType, Long targetId, AuditAction action, Long operatorId, String reason) {
        return auditLogRepository.save(new AuditLog(targetType, targetId, action, operatorId, reason));
    }
}
