package com.qinglan.review.entity;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 申诉：作者对被处置的内容申请复审。
 *
 * <p>表上的 {@code uk_appeal_target} 保证每个目标只受理一次申诉；「复审人 ≠ 原处置人」
 * 的校验在 {@code AppealService} 完成（需要读被处置内容的 {@code review_by}）。</p>
 */
@Getter
@Entity
@Table(name = "appeal")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appeal extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReviewTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "appellant_id", nullable = false)
    private Long appellantId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppealStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "restored", nullable = false)
    private boolean restored;

    public static Appeal submit(ReviewTargetType targetType, Long targetId, Long appellantId, String reason) {
        Appeal appeal = new Appeal();
        appeal.targetType = targetType;
        appeal.targetId = targetId;
        appeal.appellantId = appellantId;
        appeal.reason = reason;
        appeal.status = AppealStatus.PENDING;
        appeal.restored = false;
        return appeal;
    }

    /** 复审通过：内容恢复（恢复动作本身由 Service 在同一事务内执行） */
    public void accept(Long reviewerId) {
        assertPending();
        this.status = AppealStatus.ACCEPTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.restored = true;
    }

    /** 复审不通过：维持原判 */
    public void reject(Long reviewerId) {
        assertPending();
        this.status = AppealStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.restored = false;
    }

    private void assertPending() {
        if (status != AppealStatus.PENDING) {
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
    }
}
