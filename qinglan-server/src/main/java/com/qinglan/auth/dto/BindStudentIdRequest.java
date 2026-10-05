package com.qinglan.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BindStudentIdRequest(
        @NotBlank(message = "邮箱认证凭证不能为空")
        String emailToken,

        @NotBlank(message = "学号不能为空")
        @Pattern(regexp = "[0-9A-Za-z]{4,24}", message = "学号格式不正确")
        String studentId
) {
}
