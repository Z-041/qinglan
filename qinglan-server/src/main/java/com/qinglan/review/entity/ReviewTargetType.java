package com.qinglan.review.entity;

import com.qinglan.common.audit.AuditTargetType;

/**
 * 审核体系的目标类型。
 *
 * <p>举报与申诉共用：举报可指向帖子或评论，申诉指向被处置的同一批目标。</p>
 */
public enum ReviewTargetType {
    POST,
    COMMENT;

    /**
     * 映射到审计日志的目标类型。两者取值一致（POST / COMMENT）但分属不同枚举，需显式映射；
     * 此前在三个 Service 里各写一份 switch，收敛到本枚举一处，新增目标类型只改这里。
     */
    public AuditTargetType auditTarget() {
        return switch (this) {
            case POST -> AuditTargetType.POST;
            case COMMENT -> AuditTargetType.COMMENT;
        };
    }
}
