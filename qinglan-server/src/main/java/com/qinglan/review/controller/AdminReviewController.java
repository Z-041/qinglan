package com.qinglan.review.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.DismissReportsRequest;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.TakeDownPostRequest;
import com.qinglan.review.service.AppealService;
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
 * <p>权限为类级 {@code ADMIN / SUPER_ADMIN} 且默认拒绝：未带角色的请求根本到不了方法体。
 * Controller 只做取参、校验与取当前操作人，业务判断全在 Service。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final ReviewQueueService reviewQueueService;
    private final AppealService appealService;

    /** 举报队列：按举报数降序 + 等待时长升序，游标分页 */
    @GetMapping("/review-queue")
    public ApiResponse<CursorPage<ReviewQueueItemResponse>> queue(
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(reviewQueueService.queue(cursor));
    }

    @PostMapping("/posts/{postId}/approve")
    public ApiResponse<Void> approve(@PathVariable Long postId) {
        reviewService.approve(postId, CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{postId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.reject(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 下架；请求体带 {@code banDays} 即「下架并封禁」 */
    @PostMapping("/posts/{postId}/take-down")
    public ApiResponse<Void> takeDown(@PathVariable Long postId,
                                      @Valid @RequestBody TakeDownPostRequest request) {
        reviewService.takeDown(postId, CurrentUser.requireUserId(), request);
        return ApiResponse.ok();
    }

    /** 恢复可见；申诉通过也走这里 */
    @PostMapping("/posts/{postId}/restore")
    public ApiResponse<Void> restore(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.restore(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{postId}/hide-contact")
    public ApiResponse<Void> hideContact(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.hideContact(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 撤销隐藏联系方式：纠正「隐藏」的误判，联系方式恢复为认证用户可见 */
    @PostMapping("/posts/{postId}/show-contact")
    public ApiResponse<Void> showContact(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.showContact(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/comments/{commentId}/remove")
    public ApiResponse<Void> removeComment(@PathVariable Long commentId, @Valid @RequestBody ReasonRequest request) {
        reviewService.removeComment(commentId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 驳回举报，按目标一次结清；评论没有举报计数，仅帖子会清零 */
    @PostMapping("/reports/dismiss")
    public ApiResponse<Void> dismissReports(@Valid @RequestBody DismissReportsRequest request) {
        reviewService.dismissReports(request, CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    /**
     * 维持下架：确认举报成立，结案该帖全部待处理举报（不动内容状态）。
     * 专治「举报数达阈值自动降级」后举报仍挂在队列里、却无合法 UPHELD 入口的缺口。
     */
    @PostMapping("/posts/{postId}/uphold-reports")
    public ApiResponse<Void> upholdReports(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.upholdReports(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 复审申诉：通过则恢复内容可见。复审人不得是原处置人（AppealService 校验） */
    @PostMapping("/appeals/{appealId}/accept")
    public ApiResponse<Void> acceptAppeal(@PathVariable Long appealId,
                                          @Valid @RequestBody ReasonRequest request) {
        appealService.accept(appealId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 复审申诉：维持原判，内容不恢复 */
    @PostMapping("/appeals/{appealId}/reject")
    public ApiResponse<Void> rejectAppeal(@PathVariable Long appealId,
                                          @Valid @RequestBody ReasonRequest request) {
        appealService.reject(appealId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }
}
