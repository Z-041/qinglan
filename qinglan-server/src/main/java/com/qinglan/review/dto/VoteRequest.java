package com.qinglan.review.dto;

import com.qinglan.review.entity.VoteDecision;
import jakarta.validation.constraints.NotNull;

/**
 * 对提案投票（P5 二期）。
 *
 * <p>只有同意 / 不同意两种（{@link VoteDecision}），没有弃权——不投就是弃权，
 * 且未投票者计入分母（方案 5.2）。</p>
 */
public record VoteRequest(
        @NotNull(message = "请选择投票选项")
        VoteDecision decision
) {
}
