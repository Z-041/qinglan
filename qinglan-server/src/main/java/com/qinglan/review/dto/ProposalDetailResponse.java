package com.qinglan.review.dto;

import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.VoteDecision;

import java.time.Instant;

/**
 * 提案详情（P5 二期，接口 7.4）——列表行 + 理由 / 结论。
 *
 * <p>同样不含发起人身份（见 {@link ProposalSummaryResponse}）。{@code resolution} 是结案说明：
 * 「表决通过（提案 #12，4/6 同意）」或「超管裁决（提案 #12）」等，供委员会了解结论由来。</p>
 *
 * @param resolvedAt 结案时刻；未结案（OPEN / ESCALATED）为 null
 */
public record ProposalDetailResponse(
        Long proposalId,
        ReviewTargetType targetType,
        Long targetId,
        String preview,
        ProposalAction action,
        ProposalStatus status,
        ReportReason reasonCode,
        String detail,
        String resolution,
        int agreeCount,
        int disagreeCount,
        int needed,
        int votedCount,
        VoteDecision myDecision,
        Instant createdAt,
        Instant resolvedAt
) {
}
