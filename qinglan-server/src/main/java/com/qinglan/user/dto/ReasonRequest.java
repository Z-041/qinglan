package com.qinglan.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(
        @NotBlank(message = "理由不能为空")
        @Size(max = 500, message = "理由长度超出限制")
        String reason
) {
}
