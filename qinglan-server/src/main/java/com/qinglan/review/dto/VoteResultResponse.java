package com.qinglan.review.dto;

import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.VoteDecision;

/**
 * 投票后的汇总（P5 二期，接口 7.4）。
 *
 * <p>对委员会只回汇总，不回「谁投了什么」（方案 D8）——{@code myDecision} 只代表调用者自己那票。</p>
 *
 * @param status      投票判定后的提案状态：仍 OPEN / 已达阈值 APPROVED / 不可能过半 ESCALATED
 * @param needed      通过所需同意票数（分子为分母的一半 + 1）
 * @param myDecision  调用者本次投的选项
 */
public record VoteResultResponse(
        ProposalStatus status,
        int agreeCount,
        int disagreeCount,
        int needed,
        VoteDecision myDecision
) {
}
