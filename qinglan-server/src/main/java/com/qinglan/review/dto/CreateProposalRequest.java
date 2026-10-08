package com.qinglan.review.dto;

import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起处置提案（P5 二期，接口 7.4）。
 *
 * <p>{@code targetType} 与 {@code action} 都出现在请求里，是为了让调用方明确「对什么做什么」；
 * 服务端会校验两者匹配（{@code action.targetType() == targetType}），不匹配即 400——
 * 目标类型真正的来源是动作本身，请求里的这个字段只是一道显式的自检。</p>
 *
 * @param detail 处置说明，会随理由展示给作者；上限 400 与下架请求一致，
 *               给前面的违规类型码与分隔符留余量（列总长 500）
 */
public record CreateProposalRequest(
        @NotNull(message = "请选择目标类型")
        ReviewTargetType targetType,

        @NotNull(message = "请选择目标")
        Long targetId,

        @NotNull(message = "请选择处置动作")
        ProposalAction action,

        @NotNull(message = "请选择违规类型")
        ReportReason reasonCode,

        @Size(max = 400, message = "处置说明不能超过 400 字")
        String detail
) {
}
