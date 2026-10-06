package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.config.ReviewProperties;
import com.qinglan.review.entity.Appeal;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.AppealRepository;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * 申诉与复审。
 *
 * <p>只受理「已下架」帖子的申诉：下架是唯一带恢复入口的处置（不变量 #5 里
 * {@code TakenDown → Approved}），其余处置没有对应的回退路径，受理申诉无意义。</p>
 *
 * <p>两条硬规则：</p>
 * <ul>
 *   <li>每帖限一次（{@code appeal.uk_appeal_target} 兜底，业务层映射为 409）</li>
 *   <li>复审人 ≠ 原处置人——同一个管理员不能复核自己的裁决，否则申诉形同虚设</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AppealService {

    private static final Logger log = LoggerFactory.getLogger(AppealService.class);

    private final AppealRepository appealRepository;
    private final PostRepository postRepository;
    private final ReviewService reviewService;
    private final ReviewProperties reviewProperties;
    private final AuditLogService auditLogService;
    private final UserService userService;

    /**
     * 作者申诉。校验「本人 + 确已下架 + 未过期 + 未曾申诉」四关，全部通过才落记录。
     *
     * <p>与 {@code ReportService.report} 同口径：申诉也是写入口，令牌无状态（2h 内不失效），
     * 封禁/黑名单必须在此重查一次，不能指望登录时的校验兜住。</p>
     */
    @Transactional
    public void submit(Long appellantId, Long postId, String reason) {
        userService.requireUsableById(appellantId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.isAuthoredBy(appellantId)) {
            throw new BizException(ErrorCode.NOT_CONTENT_OWNER);
        }
        if (post.getStatus() != PostStatus.TAKEN_DOWN) {
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
        assertWithinWindow(post);
        if (appealRepository.findByTargetTypeAndTargetId(ReviewTargetType.POST, postId).isPresent()) {
            throw new BizException(ErrorCode.APPEAL_ALREADY_SUBMITTED);
        }
        saveAppeal(postId, appellantId, reason);
        log.info("提交申诉 postId={}，申诉人 appellantId={}", postId, appellantId);
    }

    /** 复审通过：恢复内容可见（状态迁移复用 ReviewService，避免在申诉里复刻状态机） */
    @Transactional
    public void accept(Long appealId, Long reviewerId, String reason) {
        Appeal appeal = review(appealId, reviewerId, true, reason);
        reviewService.restore(appeal.getTargetId(), reviewerId, normalize(reason));
        log.info("申诉通过 postId={}，复审人 reviewerId={}", appeal.getTargetId(), reviewerId);
    }

    /** 复审不通过：维持原判，内容不恢复 */
    @Transactional
    public void reject(Long appealId, Long reviewerId, String reason) {
        Appeal appeal = review(appealId, reviewerId, false, reason);
        log.info("申诉维持原判 postId={}，复审人 reviewerId={}", appeal.getTargetId(), reviewerId);
    }

    // ---------------------------------------------------------------- 内部方法

    /**
     * 落申诉记录。
     *
     * <p>必须 {@code saveAndFlush} 并在本方法内兜住唯一键冲突：若交给事务提交时才炸，
     * 异常会绕过业务层直接落到全局兜底分支，用户拿到 500 而不是「已申诉过」的 409。
     * 与 {@code ReportService.saveReport} 同一处理方式。</p>
     */
    private void saveAppeal(Long postId, Long appellantId, String reason) {
        Appeal appeal = Appeal.submit(ReviewTargetType.POST, postId, appellantId, reason.trim());
        try {
            appealRepository.saveAndFlush(appeal);
        } catch (DataIntegrityViolationException ex) {
            // 并发下唯一键 uk_appeal_target 只会放行一个，后到的走这里
            log.info("重复申诉被唯一键拒绝 postId={}", postId);
            throw new BizException(ErrorCode.APPEAL_ALREADY_SUBMITTED);
        }
    }

    private Appeal review(Long appealId, Long reviewerId, boolean accepted, String reason) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new BizException(ErrorCode.APPEAL_NOT_FOUND));
        Post post = postRepository.findById(appeal.getTargetId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (Objects.equals(post.getReviewBy(), reviewerId)) {
            throw new BizException(ErrorCode.SELF_REVIEW_FORBIDDEN);
        }
        if (accepted) {
            appeal.accept(reviewerId);
        } else {
            appeal.reject(reviewerId);
        }
        auditLogService.record(AuditTargetType.POST, appeal.getTargetId(), AuditAction.APPEAL_REVIEW,
                reviewerId, describe(accepted, reason));
        return appeal;
    }

    private void assertWithinWindow(Post post) {
        Instant deadline = post.getTakenDownAt().plus(reviewProperties.appealWindow());
        if (Instant.now().isAfter(deadline)) {
            throw new BizException(ErrorCode.APPEAL_WINDOW_EXPIRED);
        }
    }

    private String describe(boolean accepted, String reason) {
        String outcome = accepted ? "ACCEPTED" : "REJECTED";
        String normalized = normalize(reason);
        return normalized == null ? outcome : outcome + ": " + normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
