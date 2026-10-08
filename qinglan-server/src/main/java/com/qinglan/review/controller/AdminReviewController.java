package com.qinglan.review.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAppealItemResponse;
import com.qinglan.review.dto.AdminReviewContextResponse;
import com.qinglan.review.dto.DismissReportsRequest;
import com.qinglan.review.dto.PendingPostItemResponse;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.TakeDownPostRequest;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.service.AppealQueueService;
import com.qinglan.review.service.AppealService;
import com.qinglan.review.service.PendingPostQueueService;
import com.qinglan.review.service.ReviewContextService;
import com.qinglan.review.service.ReviewQueueService;
import com.qinglan.review.service.ReviewService;
import com.qinglan.user.dto.ReasonRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核工作台接口。所有动作写审计日志，不可删除（不变量 #7）。
 *
 * <p>权限点写在**方法级**（P5 设计 7.1）：类上不再做任何放宽，避免「类级放行 + 方法级遗漏」。
 * 读类端点用 {@code MODERATION_VIEW}、申诉相关用 {@code APPEAL_REVIEW}、核准用
 * {@code POST_REVIEW}、驳回举报用 {@code REPORT_DISMISS}，处置类（下架/移除/隐藏/维持）
 * 一期仍按 {@code PROPOSAL_CREATE} 直接放行，二期接入表决后改为「发起提案」。</p>
 *
 * <p>Controller 只做取参、校验与取当前操作人，业务判断全在 Service。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;
    private final ReviewQueueService reviewQueueService;
    private final ReviewContextService reviewContextService;
    private final AppealService appealService;
    private final AppealQueueService appealQueueService;
    private final PendingPostQueueService pendingPostQueueService;

    /** 举报队列：按举报数降序 + 等待时长升序，游标分页 */
    @GetMapping("/review-queue")
    @PreAuthorize("hasAuthority('MODERATION_VIEW')")
    public ApiResponse<CursorPage<ReviewQueueItemResponse>> queue(
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(reviewQueueService.queue(cursor));
    }

    /** 待复审申诉队列：先到先审，游标分页 */
    @GetMapping("/appeals")
    @PreAuthorize("hasAuthority('APPEAL_REVIEW')")
    public ApiResponse<CursorPage<AdminAppealItemResponse>> appeals(
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(appealQueueService.queue(cursor));
    }

    /**
     * 待审帖子队列：先审后发（设计方案第一章红线 2）下放行新帖的唯一入口，先到先审，游标分页。
     *
     * <p>与举报队列是两个来源：举报队列按「被举报目标」聚合，新帖没被举报就不会出现在那里。</p>
     */
    @GetMapping("/pending-posts")
    @PreAuthorize("hasAuthority('MODERATION_VIEW')")
    public ApiResponse<CursorPage<PendingPostItemResponse>> pendingPosts(
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(pendingPostQueueService.queue(cursor));
    }

    /** 上下文面板：作者信用分、历史违规次数、举报聚合。不出作者与举报人身份 */
    @GetMapping("/review-context")
    @PreAuthorize("hasAuthority('MODERATION_VIEW')")
    public ApiResponse<AdminReviewContextResponse> reviewContext(
            @RequestParam ReviewTargetType targetType,
            @RequestParam Long targetId) {
        return ApiResponse.ok(reviewContextService.context(targetType, targetId));
    }

    @PostMapping("/posts/{postId}/approve")
    @PreAuthorize("hasAuthority('POST_REVIEW')")
    public ApiResponse<Void> approve(@PathVariable Long postId) {
        reviewService.approve(postId, CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{postId}/reject")
    @PreAuthorize("hasAuthority('POST_REVIEW')")
    public ApiResponse<Void> reject(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.reject(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 下架；请求体带 {@code banDays} 即「下架并封禁」 */
    @PostMapping("/posts/{postId}/take-down")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<Void> takeDown(@PathVariable Long postId,
                                      @Valid @RequestBody TakeDownPostRequest request) {
        reviewService.takeDown(postId, CurrentUser.requireUserId(), request);
        return ApiResponse.ok();
    }

    /** 恢复可见；申诉通过也走这里 */
    @PostMapping("/posts/{postId}/restore")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<Void> restore(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.restore(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{postId}/hide-contact")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<Void> hideContact(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.hideContact(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 撤销隐藏联系方式：纠正「隐藏」的误判，联系方式恢复为认证用户可见 */
    @PostMapping("/posts/{postId}/show-contact")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<Void> showContact(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.showContact(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/comments/{commentId}/remove")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<Void> removeComment(@PathVariable Long commentId, @Valid @RequestBody ReasonRequest request) {
        reviewService.removeComment(commentId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 驳回举报，按目标一次结清；评论没有举报计数，仅帖子会清零 */
    @PostMapping("/reports/dismiss")
    @PreAuthorize("hasAuthority('REPORT_DISMISS')")
    public ApiResponse<Void> dismissReports(@Valid @RequestBody DismissReportsRequest request) {
        reviewService.dismissReports(request, CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    /**
     * 维持下架：确认举报成立，结案该帖全部待处理举报（不动内容状态）。
     * 专治「举报数达阈值自动降级」后举报仍挂在队列里、却无合法 UPHELD 入口的缺口。
     */
    @PostMapping("/posts/{postId}/uphold-reports")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<Void> upholdReports(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.upholdReports(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 复审申诉：通过则恢复内容可见。复审人不得是原处置人（AppealService 校验） */
    @PostMapping("/appeals/{appealId}/accept")
    @PreAuthorize("hasAuthority('APPEAL_REVIEW')")
    public ApiResponse<Void> acceptAppeal(@PathVariable Long appealId,
                                          @Valid @RequestBody ReasonRequest request) {
        appealService.accept(appealId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 复审申诉：维持原判，内容不恢复 */
    @PostMapping("/appeals/{appealId}/reject")
    @PreAuthorize("hasAuthority('APPEAL_REVIEW')")
    public ApiResponse<Void> rejectAppeal(@PathVariable Long appealId,
                                          @Valid @RequestBody ReasonRequest request) {
        appealService.reject(appealId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }
}
