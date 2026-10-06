package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 提交举报。
 *
 * <p>只收目标与理由，不收任何举报人字段——举报人一律取自登录态。举报记录不得带出举报人
 * 身份（不变量 #1 的引申：审核队列只展示内容与理由，不展示「谁举报的」）。</p>
 */
public record CreateReportRequest(
        @NotNull(message = "请选择举报对象类型")
        ReviewTargetType targetType,

        @NotNull(message = "请选择举报对象")
        @Positive(message = "举报对象不合法")
        Long targetId,

        @NotNull(message = "请选择举报理由")
        ReportReason reasonCode,

        @Size(max = 500, message = "补充说明不能超过 500 字")
        String detail
) {
}
