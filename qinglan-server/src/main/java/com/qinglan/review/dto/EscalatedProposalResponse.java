package com.qinglan.review.dto;

import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReviewTargetType;

import java.time.Instant;
import java.util.List;

/**
 * 待裁决列表的一行（P5 方案 5.4）——仅超管可见（端点 {@code PROPOSAL_ARBITRATE}）。
 *
 * <p>与 {@link ProposalSummaryResponse} 的关键差别是带上了 {@link ProposalVoteItem} 明细：
 * 超管要判断「僵局该怎么裁」，必须看到每一票（决策 D8）。委员会成员走的是
 * {@code GET /api/admin/proposals}，拿不到这个字段。</p>
 */
public record EscalatedProposalResponse(
        Long proposalId,
        ReviewTargetType targetType,
        Long targetId,
        String preview,
        ProposalAction action,
        ProposalStatus status,
        int agreeCount,
        int disagreeCount,
        int needed,
        int votedCount,
        Instant createdAt,
        List<ProposalVoteItem> votes
) {
}
