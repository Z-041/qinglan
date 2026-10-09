package com.qinglan.review.service;

import com.qinglan.comment.service.CommentService;
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
import com.qinglan.review.dto.DismissReportsRequest;
import com.qinglan.review.dto.ProposalDispositionRequest;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 审核处置：管理员对内容做的每一个动作。
 *
 * <p>{@code review} 是审核体系的编排者（AGENTS.md 第三章白名单）：它单向调用
 * {@code post} / {@code comment} / {@code user}，业务模块不反向依赖它。P1 里临时挂在
 * {@code post} 模块的「放行 / 驳回」也一并迁到这里——它们是审核动作，不是发帖动作。</p>
 *
 * <p>不变量落点：#5 状态变更只走 {@link Post#transitionTo}，禁止跳态；
 * #7 每个处置都写审计日志，且审计只增不改删。</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /**
     * 驳回原因码。落库的是 {@code Rejected.explanation()}（审核员填的自由文本），
     * 码本身只用于状态机载荷与后续统计，前端驳回提示按自由文本原样展示。
     */
    private static final String MANUAL_REJECT_CODE = "MANUAL_REJECT";

    /** 举报反馈标题：举报成立 / 内容已被处置 */
    private static final String REPORT_UPHELD_TITLE = "你举报的内容已处理";
    /** 举报反馈标题：举报被驳回（内容不违规） */
    private static final String REPORT_DISMISSED_TITLE = "你的举报未获支持";

    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final CommentService commentService;
    private final AuditLogService auditLogService;
    private final NotificationPort notificationPort;

    /**
     * 放行：Pending → Reviewing → Approved。
     *
     * <p>状态机禁止跳态（不变量 #5），所以这里在同一事务内原子地走完两步。P2 已接入举报队列，
     * 但「发布轻审」仍是默认放行路径，认领与放行由同一人完成属于正常情况。</p>
     */
    @Transactional
    public void approve(Long postId, Long operatorId) {
        Post post = requirePost(postId);
        Instant now = Instant.now();
        post.transitionTo(new ReviewState.Reviewing(operatorId, now));
        post.transitionTo(new ReviewState.Approved(operatorId, now));
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_APPROVE, operatorId, null);
        notificationPort.notify(post.getAuthorId(), operatorId,
                new NotificationMessage(NotificationType.REVIEW_RESULT, "你的帖子已通过审核", null, "/posts/" + postId));
        log.info("放行帖子 postId={}，操作人 operatorId={}", postId, operatorId);
    }

    /** 驳回：Reviewing → Rejected，理由对作者可见（同样先经 Reviewing，不跳态） */
    @Transactional
    public void reject(Long postId, Long operatorId, String reason) {
        Post post = requirePost(postId);
        post.transitionTo(new ReviewState.Reviewing(operatorId, Instant.now()));
        post.transitionTo(new ReviewState.Rejected(MANUAL_REJECT_CODE, reason));
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_REJECT, operatorId, reason);
        // 驳回理由对作者可见，这里一并作为通知正文带出
        notificationPort.notify(post.getAuthorId(), operatorId,
                new NotificationMessage(NotificationType.REVIEW_RESULT, "你的帖子未通过审核", reason, "/posts/" + postId));
        log.info("驳回帖子 postId={}，操作人 operatorId={}", postId, operatorId);
    }

    /**
     * 下架（Approved → TakenDown）：作者可见、他人不可见，可申诉。
     *
     * <p>同时把该帖的待处理举报一并结案（置 UPHELD）并把 {@code report_count} 清零——
     * 处置完还挂在队列里等于没处理完。清零用「减去本次结案条数」而非直接写 0：
     * 计数是原子维护的，直接写会覆盖掉并发期间新增的举报。</p>
     *
     * <p>调用方只有 P5 二期的表决执行（{@link ProposalService}，operator 为系统哨兵）。
     * 曾经的「下架并封禁」（{@code banDays}）已移除：表决机制下不能先封禁再等投票，封禁改由
     * 用户管理页单独执行（{@code USER_MANAGE}）。</p>
     */
    @Transactional
    public void takeDown(Long postId, Long operatorId, ProposalDispositionRequest request) {
        Post post = requirePost(postId);
        String reason = describe(request.reasonCode(), request.detail());
        post.transitionTo(new ReviewState.TakenDown(operatorId, reason, Instant.now()));

        int handled = upholdPendingReports(ReviewTargetType.POST, postId, operatorId);
        // 必须最后调用：该方法 flushAutomatically 落定上面的改动，再 clearAutomatically 清一级缓存
        postRepository.adjustReportCount(postId, -handled);

        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_TAKEDOWN, operatorId, reason);
        // 下架理由对作者可见（申诉入口在详情里）
        notificationPort.notify(post.getAuthorId(), operatorId,
                new NotificationMessage(NotificationType.REVIEW_RESULT, "你的帖子已被下架", reason, "/posts/" + postId));
        log.info("下架帖子 postId={} 结案举报数={}，操作人 operatorId={}", postId, handled, operatorId);
    }

    /**
     * 恢复可见（TakenDown → Approved）。申诉通过走同一条路径。
     *
     * <p><b>同时结案该帖的待处理举报</b>：恢复即「认定原举报不成立」，因此把待处理举报置为
     * {@code DISMISSED} 并原子回退 {@code report_count}。不清这一步会留下两个后果（R-31）：
     * 自动降级路径刻意把举报留在 PENDING，恢复后计数仍 ≥ 阈值，于是下一条举报立刻让帖子
     * 再次自动下架，「恢复」形同虚设；且队列里会长期挂着一条 targetStatus=APPROVED 的行。
     * 手工下架路径的举报在 {@link #takeDown} 时已结案，这里自然成为空操作。</p>
     *
     * <p><b>幂等</b>：已是可见态时直接返回，不迁移也不写审计。申诉存在「作者申诉后、
     * 复审前另一位管理员已手工恢复」的窗口，若此处仍硬走状态机，复审通过会因为
     * 「Approved → Approved 不是合法迁移」而整体回滚，连带把申诉状态也回退掉。</p>
     */
    @Transactional
    public void restore(Long postId, Long operatorId, String reason) {
        Post post = requirePost(postId);
        if (post.getStatus() == PostStatus.APPROVED) {
            log.info("帖子已可见，恢复按幂等跳过 postId={}，操作人 operatorId={}", postId, operatorId);
            return;
        }
        post.transitionTo(new ReviewState.Approved(operatorId, Instant.now()));
        int dismissed = dismissPendingReports(postId, operatorId);
        if (dismissed > 0) {
            // 必须最后调用：flushAutomatically 落定上面的状态迁移与举报结案，再 clearAutomatically 清一级缓存
            postRepository.adjustReportCount(postId, -dismissed);
        }
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_RESTORE, operatorId, reason);
        if (dismissed > 0) {
            // 结案举报是恢复的副作用，单独留一条痕迹，便于回查「谁把举报判成不成立」
            auditLogService.record(AuditTargetType.POST, postId, AuditAction.REPORT_DISMISS, operatorId, reason);
        }
        log.info("恢复帖子 postId={} 结案举报数={}，操作人 operatorId={}", postId, dismissed, operatorId);
    }

    /**
     * 维持下架：确认举报成立，结案该帖全部待处理举报并清零待处理计数。
     *
     * <p>补的是「自动降级」留下的缺口：举报数达阈值自动下架时，举报故意保持
     * {@code PENDING} 好让帖子留在队列里等人工确认。管理员若认同内容应当下架，
     * 此时既不能走 {@link #takeDown}（{@code TakenDown → TakenDown} 非法迁移），
     * 也不该走 {@link #dismissReports}（那是「内容不违规」）。本方法只结案举报、
     * 不改内容状态——内容已经在架下了，没有状态可迁。</p>
     */
    @Transactional
    public void upholdReports(Long postId, Long operatorId, String reason) {
        Post post = requirePost(postId);
        if (post.getStatus() != PostStatus.TAKEN_DOWN) {
            // 内容还可见时「维持下架」无从谈起：该走 takeDown（首次下架）或 dismissReports（内容不违规）
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, postId, ReportStatus.PENDING);
        if (pending.isEmpty()) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }
        pending.forEach(report -> report.uphold(operatorId));
        // 记下处置人：自动降级时 review_by 是 null（trigger 不是人），不记的话同一个管理员
        // 随后还能复审该帖申诉，而「复审人 ≠ 原处置人」正是靠这个字段判定（设计方案 113 行）
        post.recordHandler(operatorId);
        // 与 takeDown/dismissReports 同源：按结案条数原子回退，不直接写 0，避免覆盖并发新增的举报
        postRepository.adjustReportCount(postId, -pending.size());
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.REPORT_UPHOLD,
                operatorId, normalize(reason));
        notifyReporters(pending, REPORT_UPHELD_TITLE, ReviewTargetType.POST, postId, operatorId);
        log.info("维持下架并结案举报 postId={} 结案数={}，操作人 operatorId={}", postId, pending.size(), operatorId);
    }

    /**
     * 隐藏联系方式：只改可见性标记，不清空 {@code contact} 值——处罚可能被撤销，清空就找不回了。
     * 对外是否返回仍由 {@link Post#contactVisibleTo} 统一裁决（不变量 #2）。
     */
    @Transactional
    public void hideContact(Long postId, Long operatorId, String reason) {
        Post post = requirePost(postId);
        post.hideContact();
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_HIDE_CONTACT, operatorId, reason);
        log.info("隐藏联系方式 postId={}，操作人 operatorId={}", postId, operatorId);
    }

    /**
     * 撤销隐藏联系方式：把可见性标记改回 {@code VISIBLE}。
     *
     * <p>隐藏本身是对「疑似诈骗」这类判定的处置，判定可能是错的，必须留一条撤销路径；
     * 否则一次误操作会让联系方式永久只对作者本人可见，且没有任何接口能纠正。</p>
     */
    @Transactional
    public void showContact(Long postId, Long operatorId, String reason) {
        Post post = requirePost(postId);
        post.showContact();
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_SHOW_CONTACT, operatorId, reason);
        log.info("恢复联系方式可见 postId={}，操作人 operatorId={}", postId, operatorId);
    }

    /**
     * 移除评论——评论先发后审，这是它唯一的兜底处置。
     *
     * <p>移除细节（连同回复、硬删点赞行）交给 {@link CommentService}：那里是评论内部结构的
     * 唯一知情人，重复实现一遍必然漏掉某一步。</p>
     */
    @Transactional
    public void removeComment(Long commentId, Long operatorId, String reason) {
        commentService.removeByAdmin(commentId, operatorId, reason);
        int handled = upholdPendingReports(ReviewTargetType.COMMENT, commentId, operatorId);
        log.info("移除评论 commentId={} 结案举报数={}，操作人 operatorId={}", commentId, handled, operatorId);
    }

    /**
     * 驳回举报：内容不违规。
     *
     * <p>按目标驳回，一次结清该目标下所有待处理举报——队列是目标聚合的，留着几条没处理
     * 等于管理员还得再点一次。评论没有 {@code report_count} 列，只有帖子需要减计数。</p>
     */
    @Transactional
    public void dismissReports(DismissReportsRequest request, Long operatorId) {
        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdAndStatus(
                request.targetType(), request.targetId(), ReportStatus.PENDING);
        if (pending.isEmpty()) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }
        pending.forEach(report -> report.dismiss(operatorId));
        if (request.targetType() == ReviewTargetType.POST) {
            postRepository.adjustReportCount(request.targetId(), -pending.size());
        }
        auditLogService.record(request.targetType().auditTarget(), request.targetId(),
                AuditAction.REPORT_DISMISS, operatorId, normalize(request.reason()));
        notifyReporters(pending, REPORT_DISMISSED_TITLE, request.targetType(), request.targetId(), operatorId);
        log.info("驳回举报 targetType={} targetId={} 共 {} 条，操作人 operatorId={}",
                request.targetType(), request.targetId(), pending.size(), operatorId);
    }

    // ---------------------------------------------------------------- 内部方法

    private Post requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    /** 把某目标下所有待处理举报置为成立，返回结案条数；成立即向各举报人反馈（REPORT_FEEDBACK） */
    private int upholdPendingReports(ReviewTargetType targetType, Long targetId, Long operatorId) {
        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdAndStatus(
                targetType, targetId, ReportStatus.PENDING);
        pending.forEach(report -> report.uphold(operatorId));
        notifyReporters(pending, REPORT_UPHELD_TITLE, targetType, targetId, operatorId);
        return pending.size();
    }

    /**
     * 把某帖所有待处理举报置为「不成立」并反馈举报人，返回结案条数。
     *
     * <p>与 {@link #dismissReports} 的区别：没有待处理举报时返回 0 而不抛错——它是
     * {@link #restore} 的副作用（手工下架路径本就没有待处理举报），不是管理员的独立诉求。</p>
     */
    private int dismissPendingReports(Long postId, Long operatorId) {
        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, postId, ReportStatus.PENDING);
        if (pending.isEmpty()) {
            return 0;
        }
        pending.forEach(report -> report.dismiss(operatorId));
        notifyReporters(pending, REPORT_DISMISSED_TITLE, ReviewTargetType.POST, postId, operatorId);
        return pending.size();
    }

    /**
     * 举报处置完毕，逐条通知**各自的举报人**（同一目标的多条举报分别通知）。
     *
     * <p>收件人是 {@code report.reporterId}，不聚合、不去重——举报人身份只在此处用于投递，
     * 不出现在任何对外 DTO 里（不变量 #1 的引申）。</p>
     */
    private void notifyReporters(List<Report> reports, String title,
                                 ReviewTargetType targetType, Long targetId, Long operatorId) {
        String link = reportFeedbackLink(targetType, targetId);
        for (Report report : reports) {
            notificationPort.notify(report.getReporterId(), operatorId,
                    new NotificationMessage(NotificationType.REPORT_FEEDBACK, title, null, link));
        }
    }

    /**
     * 举报反馈通知的详情回链：帖子直接给 {@code /posts/{id}}；评论回链到其<b>所属帖子</b>
     * （评论模块负责解析，不在本层展开跨模块查询）。解析不到（评论已不存在）时为 {@code null}，
     * 通知退化为不可点的普通条目，前端按 null 处理。</p>
     */
    private String reportFeedbackLink(ReviewTargetType targetType, Long targetId) {
        if (targetType == ReviewTargetType.POST) {
            return "/posts/" + targetId;
        }
        return commentService.findPostId(targetId).map(postId -> "/posts/" + postId).orElse(null);
    }

    /**
     * 处置理由 = 违规类型码 + 可选说明。写成「码: 说明」而不是只存说明，
     * 是为了后续能按码统计与映射文案；说明上限 400 保证拼完不超过列长 500。
     */
    private String describe(ReportReason reasonCode, String detail) {
        if (detail == null || detail.isBlank()) {
            return reasonCode.name();
        }
        return reasonCode.name() + ": " + detail.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
