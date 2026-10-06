package com.qinglan.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 作者申诉（每帖限一次）。
 *
 * <p>只收目标帖子与申诉理由——申诉人一律取自登录态，且服务端会校验其确为该帖作者，
 * 请求体不得携带任何身份字段。</p>
 */
public record SubmitAppealRequest(
        @NotNull(message = "请选择申诉的帖子")
        @Positive(message = "帖子不合法")
        Long postId,

        @NotBlank(message = "请填写申诉理由")
        @Size(max = 500, message = "申诉理由不能超过 500 字")
        String reason
) {
}
