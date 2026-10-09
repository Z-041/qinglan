package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.config.ReviewProperties;
import com.qinglan.review.dto.CreateReportRequest;
import com.qinglan.review.dto.MyReportStatusResponse;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 举报受理。
 *
 * <p>落点与不变量：#4 举报不可刷量——先查重、再靠 {@code uk_report_target_reporter} 兜住并发，
 * 重复一律回 409 而不是静默成功。</p>
 *
 * <p>跨模块依赖：{@code review → post/comment/user}（AGENTS.md 第三章白名单）。{@code review}
 * 是审核体系的编排者，业务模块不反向依赖它。</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 自动下架时写入 {@code post.review_reason} 的机器可读原因，前端按码映射文案 */
    private static final String AUTO_TAKEDOWN_REASON = "AUTO_REPORT_THRESHOLD";

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final NotificationPort notificationPort;
    private final ReviewProperties reviewProperties;

    /**
     * 当前用户在某目标上的举报状态（学生端详情页展示处理进度、并据此提前禁用重复举报）。
     *
     * <p><b>只查调用者自己</b>：按 {@code (targetType, targetId, reporterId)} 定位，{@code reporterId}
     * 一律取自登录态，故绝不会带出他人举报（不变量 #1）。未举报时返回 {@code reported=false}
     * 与全 null，而不是 404——「没举报过」是正常状态，不是错误。</p>
     */
    @Transactional(readOnly = true)
    public MyReportStatusResponse myReportStatus(
            Long reporterId, ReviewTargetType targetType, Long targetId) {
        return reportRepository.findByTargetTypeAndTargetIdAndReporterId(targetType, targetId, reporterId)
                .map(report -> new MyReportStatusResponse(
                        true, report.getStatus(), report.getCreatedAt(), report.getHandledAt()))
                .orElseGet(() -> new MyReportStatusResponse(false, null, null, null));
    }

    /**
     * 提交举报。
     *
     * <p>帖子目标会累加 {@code post.report_count}；达到阈值时自动下架并转人工复核。
     * 评论没有审核状态机（先发后审），只记录举报，由管理员在队列里手工移除。</p>
     */
    @Transactional
    public void report(Long reporterId, CreateReportRequest request) {
        userService.requireUsableById(reporterId);
        Long authorId = requireReportableAuthor(request.targetType(), request.targetId(), reporterId);
        if (authorId.equals(reporterId)) {
            throw new BizException(ErrorCode.SELF_REPORT_FORBIDDEN);
        }
        // 预检给出干净的 409；并发下仍以唯一键为准，两者的用户可见结果一致
        if (reportRepository.findByTargetTypeAndTargetIdAndReporterId(
                request.targetType(), request.targetId(), reporterId).isPresent()) {
            throw new BizException(ErrorCode.ALREADY_REPORTED);
        }
        Report saved = saveReport(reporterId, request);
        log.info("提交举报 reportId={} targetType={} targetId={}",
                saved.getId(), request.targetType(), request.targetId());

        if (request.targetType() == ReviewTargetType.POST) {
            postRepository.adjustReportCount(request.targetId(), 1);
            autoTakeDownIfNeeded(request.targetId());
        }
    }

    // ---------------------------------------------------------------- 内部方法

    private Report saveReport(Long reporterId, CreateReportRequest request) {
        Report report = Report.submit(request.targetType(), request.targetId(), reporterId,
                request.reasonCode(), normalizeDetail(request.detail()));
        try {
            return reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException ex) {
            // 两个请求同时提交时唯一键只会放行一个，后到的走这里
            log.info("重复举报被唯一键拒绝 targetType={} targetId={}", request.targetType(), request.targetId());
            throw new BizException(ErrorCode.ALREADY_REPORTED);
        }
    }

    /**
     * 校验举报目标存在且对举报人可见，返回内容作者 id。
     *
     * <p>不可见的目标（待审/已下架且非本人）一律按「不存在」返回，不泄露存在性。</p>
     */
    private Long requireReportableAuthor(ReviewTargetType targetType, Long targetId, Long viewerId) {
        return switch (targetType) {
            case POST -> postRepository.findById(targetId)
                    .filter(post -> post.canBeViewedBy(viewerId))
                    .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND))
                    .getAuthorId();
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId)
                        .filter(candidate -> !candidate.isRemoved())
                        .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
                // 子资源必须先校验父帖可见性，否则已下架帖子的评论会成为可写后门
                postRepository.findById(comment.getPostId())
                        .filter(post -> post.canBeViewedBy(viewerId))
                        .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
                yield comment.getAuthorId();
            }
        };
    }

    /**
     * 待处理举报数达到阈值即自动下架，转人工复核（设计方案：N=3）。
     *
     * <p>不跳过状态机：{@code Approved → TakenDown} 是合法迁移，非法迁移会抛错而不是写坏数据。
     * {@code review_by} 留空表示「非人工处置」，审计日志用 {@code SYSTEM_OPERATOR_ID} 留痕。</p>
     *
     * <p>举报不在此处置为 UPHELD——保持待处理，帖子才会留在队列里等人工确认，
     * 也才有「驳回举报 → 恢复」的余地。</p>
     */
    private void autoTakeDownIfNeeded(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        // 并发下可能已有另一个请求先下了架；此时再迁移会违反状态机，直接返回
        if (post.getStatus() != PostStatus.APPROVED
                || post.getReportCount() < reviewProperties.autoTakeDownThreshold()) {
            return;
        }
        post.transitionTo(new ReviewState.TakenDown(null, AUTO_TAKEDOWN_REASON, Instant.now()));
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_AUTO_TAKEDOWN,
                AuditLogService.SYSTEM_OPERATOR_ID, AUTO_TAKEDOWN_REASON);
        // 自动下架也要通知作者；trigger 不是人，actor 用系统哨兵值（与审计一致）
        notificationPort.notify(post.getAuthorId(), AuditLogService.SYSTEM_OPERATOR_ID,
                new NotificationMessage(NotificationType.REVIEW_RESULT, "你的帖子已被下架",
                        AUTO_TAKEDOWN_REASON, "/posts/" + postId));
        log.info("举报数达阈值自动下架 postId={} reportCount={}", postId, post.getReportCount());
    }

    private String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return detail.trim();
    }
}
