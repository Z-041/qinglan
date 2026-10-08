package com.qinglan.review.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.ArbitrateRequest;
import com.qinglan.review.dto.CreateProposalRequest;
import com.qinglan.review.dto.CreateProposalResponse;
import com.qinglan.review.dto.EscalatedProposalResponse;
import com.qinglan.review.dto.ProposalDetailResponse;
import com.qinglan.review.dto.ProposalSummaryResponse;
import com.qinglan.review.dto.VoteRequest;
import com.qinglan.review.dto.VoteResultResponse;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.service.ProposalService;
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
 * 处置提案与表决接口（P5 二期，设计 7.4）。
 *
 * <p>权限点逐方法声明（决策 D1 / D11）：发起要 {@code PROPOSAL_CREATE}，浏览与投票要
 * {@code PROPOSAL_VOTE}。前端隐藏按钮只是体验层，真正的防线在这里——手动调接口同样会被拦。</p>
 *
 * <p>响应只给汇总与本投票人的选择，不给发起人与其他投票人身份（决策 D8）。超管的「待裁决」与
 * 裁决端点在四步落地（{@code PROPOSAL_ARBITRATE}）。</p>
 */
@RestController
@RequestMapping("/api/admin/proposals")
@RequiredArgsConstructor
public class AdminProposalController {

    private final ProposalService proposalService;

    /** 发起处置提案；返回提案号，发起人随后自行投票 */
    @PostMapping
    @PreAuthorize("hasAuthority('PROPOSAL_CREATE')")
    public ApiResponse<CreateProposalResponse> create(@Valid @RequestBody CreateProposalRequest request) {
        Long proposalId = proposalService.create(request, CurrentUser.requireUserId());
        return ApiResponse.ok(new CreateProposalResponse(proposalId));
    }

    /**
     * 提案列表（游标分页）。
     *
     * @param status 默认 OPEN（进行中）；可传 ESCALATED 看僵局待裁决
     * @param mine   只看自己发起的（「我的提案」视图）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PROPOSAL_VOTE')")
    public ApiResponse<CursorPage<ProposalSummaryResponse>> list(
            @RequestParam(required = false) ProposalStatus status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(proposalService.page(status, mine, cursor, CurrentUser.requireUserId()));
    }

    @GetMapping("/{proposalId}")
    @PreAuthorize("hasAuthority('PROPOSAL_VOTE')")
    public ApiResponse<ProposalDetailResponse> detail(@PathVariable Long proposalId) {
        return ApiResponse.ok(proposalService.detail(proposalId, CurrentUser.requireUserId()));
    }

    /** 投票；达阈值即在同一事务内执行处置，不可能过半即转超管裁决 */
    @PostMapping("/{proposalId}/vote")
    @PreAuthorize("hasAuthority('PROPOSAL_VOTE')")
    public ApiResponse<VoteResultResponse> vote(@PathVariable Long proposalId,
                                                @Valid @RequestBody VoteRequest request) {
        return ApiResponse.ok(proposalService.vote(proposalId, request.decision(), CurrentUser.requireUserId()));
    }

    /**
     * 待裁决列表（僵局提案）：仅超管（{@code PROPOSAL_ARBITRATE}）。
     *
     * <p>字面量路径优先于 {@code /{proposalId}}，不会被当成 id 解析。出参带每一票明细（决策 D8）——
     * 委员会成员拿不到这个端点，因此看不到「谁投了什么」。</p>
     */
    @GetMapping("/escalated")
    @PreAuthorize("hasAuthority('PROPOSAL_ARBITRATE')")
    public ApiResponse<CursorPage<EscalatedProposalResponse>> escalated(
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(proposalService.escalatedPage(cursor));
    }

    /** 超管裁决：准予处置则执行处置，不予处置则驳回；必须填理由，全程留痕 */
    @PostMapping("/{proposalId}/arbitrate")
    @PreAuthorize("hasAuthority('PROPOSAL_ARBITRATE')")
    public ApiResponse<Void> arbitrate(@PathVariable Long proposalId,
                                       @Valid @RequestBody ArbitrateRequest request) {
        proposalService.arbitrate(proposalId, request.decision(), request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }
}
