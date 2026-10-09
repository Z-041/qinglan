package com.qinglan.review.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAppealItemResponse;
import com.qinglan.review.dto.AdminReviewContextResponse;
import com.qinglan.review.dto.AdminReviewHistoryResponse;
import com.qinglan.review.dto.AdminTodoCountsResponse;
import com.qinglan.review.dto.CreateProposalRequest;
import com.qinglan.review.dto.CreateProposalResponse;
import com.qinglan.review.dto.DismissReportsRequest;
import com.qinglan.review.dto.PendingPostItemResponse;
import com.qinglan.review.dto.ProposalDispositionRequest;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.ReviewQueueQuery;
import com.qinglan.review.dto.ReviewQueueSort;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.service.AdminTodoCountService;
import com.qinglan.review.service.AppealQueueService;
import com.qinglan.review.service.AppealService;
import com.qinglan.review.service.PendingPostQueueService;
import com.qinglan.review.service.ProposalService;
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

import java.util.List;

/**
 * 审核工作台接口。所有动作写审计日志，不可删除（不变量 #7）。
 *
 * <p>权限点写在**方法级**（P5 设计 7.1）：类上不再做任何放宽，避免「类级放行 + 方法级遗漏」。
 * 读类端点用 {@code MODERATION_VIEW}、申诉相关用 {@code APPEAL_REVIEW}、核准用
 * {@code POST_REVIEW}、驳回举报用 {@code REPORT_DISMISS}。处置类（下架/移除/隐藏/维持）
 * **二期起不再直接处置**，改为「发起提案」（{@code PROPOSAL_CREATE} + 委员会表决，决策 D5）；
 * **撤销**已作出的处置（恢复可见 / 撤销隐藏联系方式）归 {@code DISPOSITION_REVOKE}，
 * 仅超级管理员——否则任一委员都能静默推翻多数决。</p>
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
    private final ProposalService proposalService;
    private final AdminTodoCountService adminTodoCountService;

    /**
     * 举报队列：默认按举报数降序 + 等待时长升序，游标分页；行内带该目标进行中的提案进度。
     *
     * <p>阶段 A 起支持筛选（理由多选 / 目标类型 / 只看超时）与排序（举报数降序 / 首报升序）。
     * 筛选进 SQL、分页仍是键集；**切换 sort 时游标格式随之变化**，前端需一并回传 sort。</p>
     */
    @GetMapping("/review-queue")
    @PreAuthorize("hasAuthority('MODERATION_VIEW')")
    public ApiResponse<CursorPage<ReviewQueueItemResponse>> queue(
            @RequestParam(required = false) List<ReportReason> reasons,
            @RequestParam(required = false) ReviewTargetType targetType,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) ReviewQueueSort sort,
            @RequestParam(required = false) String cursor) {
        ReviewQueueQuery query = new ReviewQueueQuery(
                reasons, targetType, Boolean.TRUE.equals(overdueOnly), sort);
        return ApiResponse.ok(reviewQueueService.queue(cursor, query, CurrentUser.requireUserId()));
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

    /**
     * 待办计数（阶段 B）：顶栏铃铛轮询的轻量接口，按权限点裁剪——无权限的分组不返回（不是返回 0）。
     *
     * <p>权限点用 {@code MODERATION_VIEW}（三档都有），组内再按权限点裁剪：
     * 待审帖子 / 待处理举报要 {@code MODERATION_VIEW}、可投票提案要 {@code PROPOSAL_VOTE}、
     * 僵局待裁决要 {@code PROPOSAL_ARBITRATE}。</p>
     */
    @GetMapping("/todo-counts")
    @PreAuthorize("hasAuthority('MODERATION_VIEW')")
    public ApiResponse<AdminTodoCountsResponse> todoCounts() {
        return ApiResponse.ok(adminTodoCountService.counts(CurrentUser.requireUserId()));
    }

    /**
     * 作者历史 / 目标处置历史（阶段 A 面板增强）。
     *
     * <p>只回 ID 与状态 / 动作，不含学号、邮箱、匿名代号（不变量 #1 / #3）。</p>
     */
    @GetMapping("/review-context/history")
    @PreAuthorize("hasAuthority('MODERATION_VIEW')")
    public ApiResponse<AdminReviewHistoryResponse> reviewHistory(
            @RequestParam ReviewTargetType targetType,
            @RequestParam Long targetId) {
        return ApiResponse.ok(reviewContextService.history(targetType, targetId));
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

    /**
     * 下架：二期起改为**发起处置提案**（P5 决策 D5），由委员会表决通过后系统才真正下架。
     *
     * <p>原请求体里的「下架并封禁」（{@code banDays}）随之去掉——表决机制下不能先封禁再等投票；
     * 封禁改由用户管理页的 {@code POST /api/admin/users/{id}/ban} 单独执行（USER_MANAGE）。
     * 动作由 URL 隐含，请求体只带违规类型与说明。</p>
     */
    @PostMapping("/posts/{postId}/take-down")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<CreateProposalResponse> takeDown(@PathVariable Long postId,
                                                        @Valid @RequestBody ProposalDispositionRequest request) {
        return propose(ProposalAction.TAKE_DOWN, postId, request);
    }

    /**
     * 恢复可见：**撤销一条已作出的下架处置**，仅超级管理员（{@code DISPOSITION_REVOKE}）。
     *
     * <p>下架是经委员会表决才成立的处置，若任一委员都能一键撤销，多数决就不构成约束。
     * 申诉复审（{@code APPEAL_REVIEW}）是另一条通道，不受此限制。</p>
     */
    @PostMapping("/posts/{postId}/restore")
    @PreAuthorize("hasAuthority('DISPOSITION_REVOKE')")
    public ApiResponse<Void> restore(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.restore(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 隐藏联系方式：二期起改为发起提案（不变量 #2 的处置侧） */
    @PostMapping("/posts/{postId}/hide-contact")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<CreateProposalResponse> hideContact(@PathVariable Long postId,
                                                           @Valid @RequestBody ProposalDispositionRequest request) {
        return propose(ProposalAction.HIDE_CONTACT, postId, request);
    }

    /**
     * 撤销隐藏联系方式：同样是**撤销一条已作出的处置**（隐藏本身要经表决），故与恢复可见同档，
     * 仅超级管理员（{@code DISPOSITION_REVOKE}）——否则「隐藏」这一处置形同虚设。
     */
    @PostMapping("/posts/{postId}/show-contact")
    @PreAuthorize("hasAuthority('DISPOSITION_REVOKE')")
    public ApiResponse<Void> showContact(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        reviewService.showContact(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }

    /** 移除评论：二期起改为发起提案——评论先发后审，这是唯一的兜底处置手段 */
    @PostMapping("/comments/{commentId}/remove")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<CreateProposalResponse> removeComment(@PathVariable Long commentId,
                                                             @Valid @RequestBody ProposalDispositionRequest request) {
        return propose(ProposalAction.REMOVE_COMMENT, commentId, request);
    }

    /** 驳回举报，按目标一次结清；评论没有举报计数，仅帖子会清零 */
    @PostMapping("/reports/dismiss")
    @PreAuthorize("hasAuthority('REPORT_DISMISS')")
    public ApiResponse<Void> dismissReports(@Valid @RequestBody DismissReportsRequest request) {
        reviewService.dismissReports(request, CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    /**
     * 维持下架：二期起改为发起提案（确认违规成立、结案该帖待处理举报）。
     * 专治「举报数达阈值自动降级」后举报仍挂在队列里、却无合法 UPHELD 入口的缺口。
     */
    @PostMapping("/posts/{postId}/uphold-reports")
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<CreateProposalResponse> upholdReports(@PathVariable Long postId,
                                                             @Valid @RequestBody ProposalDispositionRequest request) {
        return propose(ProposalAction.UPHOLD_TAKEDOWN, postId, request);
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

    /**
     * 处置类端点的统一出口：都只是「发起提案」（P5 决策 D5）。
     *
     * <p>目标类型由动作本身决定（{@link ProposalAction#targetType()}），因此不存在
     * 「对评论下架」这类不存在的组合；真正的校验与唯一性约束在 {@link ProposalService}。</p>
     */
    private ApiResponse<CreateProposalResponse> propose(ProposalAction action, Long targetId,
                                                        ProposalDispositionRequest request) {
        Long proposalId = proposalService.create(new CreateProposalRequest(
                        action.targetType(), targetId, action, request.reasonCode(), request.detail()),
                CurrentUser.requireUserId());
        return ApiResponse.ok(new CreateProposalResponse(proposalId));
    }
}
