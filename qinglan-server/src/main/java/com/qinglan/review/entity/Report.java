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
 * 举报。
 *
 * <p>不变量 #4（举报不可刷量）由数据库的 {@code uk_report_target_reporter} 兜底：
 * 同一用户对同一目标只能有一条记录，重复提交由唯一键拒绝，业务层映射为 409，
 * 不静默吞掉。</p>
 *
 * <p>不变量 #1（匿名不可关联）：举报记录只存 {@code reporterId}，任何对外 DTO
 * 与审核队列响应都不得带出举报人身份。</p>
 */
@Getter
@Entity
@Table(name = "report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReviewTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 32)
    private ReportReason reasonCode;

    @Column(name = "detail", length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    public static Report submit(ReviewTargetType targetType, Long targetId, Long reporterId,
                                ReportReason reasonCode, String detail) {
        Report report = new Report();
        report.targetType = targetType;
        report.targetId = targetId;
        report.reporterId = reporterId;
        report.reasonCode = reasonCode;
        report.detail = detail;
        report.status = ReportStatus.PENDING;
        return report;
    }

    /** 举报成立：内容已被处置 */
    public void uphold(Long operatorId) {
        assertPending();
        this.status = ReportStatus.UPHELD;
        this.handledBy = operatorId;
        this.handledAt = Instant.now();
    }

    /** 驳回举报：内容不违规，予以保留 */
    public void dismiss(Long operatorId) {
        assertPending();
        this.status = ReportStatus.DISMISSED;
        this.handledBy = operatorId;
        this.handledAt = Instant.now();
    }

    /**
     * 只允许处置待处理举报。两位管理员同时点同一个目标时，后到的会在状态已变更后
     * 走到这里，避免把已处置的举报反复改写。
     */
    private void assertPending() {
        if (status != ReportStatus.PENDING) {
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
    }
}
