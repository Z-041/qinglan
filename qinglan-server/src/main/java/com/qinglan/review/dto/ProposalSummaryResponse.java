package com.qinglan.review.dto;

import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.VoteDecision;

import java.time.Instant;

/**
 * 提案列表的一行（P5 二期，接口 7.4）。
 *
 * <p><b>刻意不含发起人身份</b>：委员会只看到「有人发起了对某内容的处置」与票数汇总，
 * 看不到是谁发起的、更看不到谁投了什么（方案 D8）——避免委员会内部对立。
 * 超管要追溯有审计日志（{@code PROPOSAL_CREATE} 记了发起人）。</p>
 *
 * @param preview    目标内容摘要（帖子 / 评论正文），超长已截断，供快速判断
 * @param needed     通过所需同意票数（按提案创建时的在册人数快照算，全程不变）
 * @param votedCount 已投票数（同意 + 不同意）
 * @param myDecision 调用者自己那票；未投为 null
 */
public record ProposalSummaryResponse(
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
        VoteDecision myDecision,
        Instant createdAt
) {
}
