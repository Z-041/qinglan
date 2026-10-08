package com.qinglan.review.dto;

import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.VoteDecision;

/**
 * 审核队列行上的「正在进行的处置提案」（P5 二期，方案 9.2）。
 *
 * <p>为什么必须有它：处置改走表决后，同目标已有进行中提案时再发起会被唯一键挡成 409。
 * 若队列行上看不到「已在表决中 2/4」，点下架的人只会收到一句冲突提示，不知道去哪儿跟进——
 * 这正是「拦截型流程必须连入口一起交付」那条教训（修复记录 经验 #41）。</p>
 *
 * <p><b>只给汇总与本投票人的选择</b>，不含任何投票人身份（决策 D8：委员会只可见汇总）。</p>
 *
 * @param status      进行中（OPEN）还是僵局待裁决（ESCALATED）
 * @param needed      通过所需同意票数（按提案创建时的在册人数快照算）
 * @param votedCount  已投票数（同意 + 不同意）
 * @param myDecision  当前操作者自己那一票；未投为 null
 */
public record ReviewQueueProposalResponse(
        Long proposalId,
        ProposalAction action,
        ProposalStatus status,
        int agreeCount,
        int needed,
        int votedCount,
        VoteDecision myDecision
) {
}
