package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 下架帖子。
 *
 * <p>违规类型复用 {@link ReportReason}——举报理由与下架理由本就是同一套分类，
 * 各造一个枚举会让「举报说 A、下架说 B」的映射无处安放。</p>
 *
 * @param detail  处置说明，写入 {@code post.review_reason}，作者可见；上限 400 而非 500，
 *                是为了给前面的违规类型码与分隔符留出余量——该列总长 500
 * @param banDays 「下架并封禁」的天数；为 {@code null} 表示只下架、不封禁
 */
public record TakeDownPostRequest(
        @NotNull(message = "请选择违规类型")
        ReportReason reasonCode,

        @Size(max = 400, message = "处置说明不能超过 400 字")
        String detail,

        @Min(value = 1, message = "封禁天数至少为 1 天")
        @Max(value = 3650, message = "封禁天数超出上限")
        Integer banDays
) {
}
