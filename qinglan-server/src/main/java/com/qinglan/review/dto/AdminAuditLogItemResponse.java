package com.qinglan.review.dto;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditTargetType;

import java.time.Instant;

/**
 * 审计日志查询的一行（P5 设计 7.5，仅超管可见）。
 *
 * <p><b>不含任何身份明文</b>：字段就是 {@code review_log} 表里已有的那几列（操作人只以 id 出现）。
 * 超管的「全见」指的是能看到全部日志，不是能看到学号 / 邮箱——不变量 #3 不因角色而例外。</p>
 *
 * @param operatorId 操作人 id；{@code 0} 是系统自动处置的哨兵值（见 AuditLogService）
 * @param reason     处置理由原文（下架理由 / 驳回说明 / 授权理由等），可能为空
 */
public record AdminAuditLogItemResponse(
        Long id,
        AuditTargetType targetType,
        Long targetId,
        AuditAction action,
        Long operatorId,
        String reason,
        Instant createdAt
) {
}
