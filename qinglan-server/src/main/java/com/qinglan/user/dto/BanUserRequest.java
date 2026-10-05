package com.qinglan.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BanUserRequest(
        @Min(value = 1, message = "封禁天数至少为 1")
        @Max(value = 3650, message = "封禁天数超出上限")
        int days,

        @NotBlank(message = "封禁理由不能为空")
        @Size(max = 500, message = "理由长度超出限制")
        String reason
) {
}
