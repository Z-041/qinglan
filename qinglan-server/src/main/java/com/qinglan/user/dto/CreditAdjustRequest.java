package com.qinglan.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreditAdjustRequest(
        @Min(value = -100, message = "单次扣分不得超过 100")
        @Max(value = 100, message = "单次加分不得超过 100")
        int delta,

        @NotBlank(message = "调整理由不能为空")
        @Size(max = 500, message = "理由长度超出限制")
        String reason
) {
}
