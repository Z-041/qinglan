package com.qinglan.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 审计日志（表 review_log）。
 *
 * <p>不变量 #7：只增不改不删。因此该实体没有任何 setter，创建时间在构造时固化；
 * 对应仓储只暴露保存与查询，刻意不继承 JpaRepository 以免带出 delete/update 能力。</p>
 */
@Getter
@Entity
@Table(name = "review_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private AuditAction action;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog(AuditTargetType targetType, Long targetId, AuditAction action, Long operatorId, String reason) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.action = action;
        this.operatorId = operatorId;
        this.reason = reason;
        this.createdAt = Instant.now();
    }
}
