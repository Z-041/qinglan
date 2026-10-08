package com.qinglan.review.dto;

import com.qinglan.review.entity.VoteDecision;

import java.time.Instant;

/**
 * 待裁决列表里的一票（P5 方案 5.4 / 决策 D8）。
 *
 * <p><b>只在 {@code PROPOSAL_ARBITRATE} 的端点里出现</b>——超管要看到「谁投了什么」才能裁决；
 * 委员会成员看不到本 DTO（他们的列表与详情只给汇总）。这条正是 D8「投票对委员会只展示汇总、
 * 超管可见每一票」的落点。</p>
 */
public record ProposalVoteItem(
        Long voterId,
        VoteDecision decision,
        Instant createdAt
) {
}
