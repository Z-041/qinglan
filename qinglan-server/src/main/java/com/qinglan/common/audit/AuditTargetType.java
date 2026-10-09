package com.qinglan.common.audit;

/**
 * 审计日志的目标类型。
 */
public enum AuditTargetType {
    USER,
    POST,
    COMMENT,
    ANNOUNCEMENT,
    /**
     * 批量导出：没有单一目标实体，{@code target_id} 固定为 {@code 0}（哨兵）。
     *
     * <p>与 {@code AuditLogService.SYSTEM_OPERATOR_ID} 同一思路——用显式哨兵而不是留空，
     * 因为 {@code target_id} 是 NOT NULL。</p>
     */
    EXPORT
}
