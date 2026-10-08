package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 处置理由：违规类型 + 补充说明（P5 二期）。
 *
 * <p>两个用途、同一个形状，所以只留一个类型：① 既有处置端点改造后的**入参**——下架 / 移除评论 /
 * 隐藏联系方式 / 维持下架这 4 个端点二期起不再直接处置，而是「发起提案」，动作由 URL 隐含
 * （如 {@code /posts/{id}/take-down} 即 TAKE_DOWN），请求体只需要理由，目标与动作都不必再传，
 * 避免调用方传错组合；② 表决通过后**执行处置**时给 {@link com.qinglan.review.service.ReviewService}
 * 的入参（避免服务层再引入一个同形状的 DTO）。</p>
 *
 * @param detail 处置说明，随理由展示给作者；上限 400，给前面的违规类型码与分隔符留余量
 */
public record ProposalDispositionRequest(
        @NotNull(message = "请选择违规类型")
        ReportReason reasonCode,

        @Size(max = 400, message = "处置说明不能超过 400 字")
        String detail
) {
}
