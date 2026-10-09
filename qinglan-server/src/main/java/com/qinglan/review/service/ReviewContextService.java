package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.audit.AuditLog;
import com.qinglan.common.audit.AuditLogRepository;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.post.entity.Post;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminReviewContextResponse;
import com.qinglan.review.dto.AdminReviewHistoryResponse;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewContextRepository;
import com.qinglan.user.entity.User;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审核上下文面板：按目标反查作者与举报聚合。
 *
 * <p>为什么按 target 反查而不是从队列带过来：{@code ReviewQueueItemResponse} 刻意不含
 * {@code authorId}（不变量 #1 的引申，已有测试断言）。作者 id 只在本方法内部流转，
 * 出参里只有信用分与违规次数，不出现任何身份字段。</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewContextService {

    /** 「作者最近动态」取多少条：够判断惯犯 / 初犯即可，不必把该作者的帖子全列出来 */
    private static final int RECENT_POST_LIMIT = 10;
    /** 「目标处置历史」取多少条：最近的处置最有参考价值 */
    private static final int DISPOSITION_LIMIT = 20;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final ReportRepository reportRepository;
    private final ReviewContextRepository reviewContextRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public AdminReviewContextResponse context(ReviewTargetType targetType, Long targetId) {
        Target target = resolveTarget(targetType, targetId);
        User author = userService.requireById(target.authorId());

        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdAndStatus(
                targetType, targetId, ReportStatus.PENDING);
        List<Long> reporterIds = reportRepository.findDistinctReporterIds(targetType, targetId);
        long repeated = reporterIds.isEmpty()
                ? 0L
                : reportRepository.countReportersWithOtherReports(reporterIds, targetType, targetId);
        List<ReportReason> reasons = pending.stream().map(Report::getReasonCode).distinct().toList();

        return new AdminReviewContextResponse(
                targetType,
                targetId,
                target.status(),
                author.getCreditScore(),
                reviewContextRepository.countAuthorViolations(target.authorId()),
                pending.size(),
                reporterIds.size(),
                repeated,
                reasons);
    }

    /**
     * 作者最近动态 + 目标处置历史（阶段 A 面板增强）。
     *
     * <p>作者 id 只在方法内部用于查帖子，出参只有帖子的 {@code id / status / createdAt}——
     * 不含 authorId、匿名代号、学号、邮箱（不变量 #1 / #3）。</p>
     */
    @Transactional(readOnly = true)
    public AdminReviewHistoryResponse history(ReviewTargetType targetType, Long targetId) {
        Target target = resolveTarget(targetType, targetId);
        List<AdminReviewHistoryResponse.AuthorPostItem> recentPosts = postRepository
                .findMinePage(target.authorId(), null, null, null, PageRequest.of(0, RECENT_POST_LIMIT))
                .stream()
                .map(post -> new AdminReviewHistoryResponse.AuthorPostItem(
                        post.getId(), post.getStatus().name(), post.getCreatedAt()))
                .toList();
        List<AdminReviewHistoryResponse.DispositionItem> dispositions = auditLogRepository
                .findDispositionHistory(targetType.auditTarget(), targetId,
                        PageRequest.of(0, DISPOSITION_LIMIT))
                .stream()
                .map(ReviewContextService::toDisposition)
                .toList();
        return new AdminReviewHistoryResponse(recentPosts, dispositions);
    }

    private static AdminReviewHistoryResponse.DispositionItem toDisposition(AuditLog log) {
        return new AdminReviewHistoryResponse.DispositionItem(
                log.getAction(), log.getOperatorId(), log.getReason(), log.getCreatedAt());
    }

    private Target resolveTarget(ReviewTargetType targetType, Long targetId) {
        return switch (targetType) {
            case POST -> postRepository.findById(targetId)
                    .map(post -> new Target(post.getAuthorId(), post.getStatus().name()))
                    .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
            case COMMENT -> commentRepository.findById(targetId)
                    .map(comment -> new Target(comment.getAuthorId(), comment.getStatus().name()))
                    .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
        };
    }

    /** 内部值对象：{@code authorId} 不出本类 */
    private record Target(Long authorId, String status) {
    }
}
