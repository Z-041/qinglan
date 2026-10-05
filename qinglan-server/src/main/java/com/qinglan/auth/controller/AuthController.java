package com.qinglan.auth.controller;

import com.qinglan.auth.dto.AuthTokenResponse;
import com.qinglan.auth.dto.BindStudentIdRequest;
import com.qinglan.auth.dto.SendCodeRequest;
import com.qinglan.auth.dto.SendCodeResponse;
import com.qinglan.auth.dto.VerifyEmailRequest;
import com.qinglan.auth.dto.VerifyEmailResponse;
import com.qinglan.auth.service.AuthService;
import com.qinglan.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发送校园邮箱验证码。
     */
    @PostMapping("/email/code")
    public ApiResponse<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return ApiResponse.ok(authService.sendEmailCode(request.email()));
    }

    /**
     * 校验验证码：老用户直接登录，新用户返回 emailToken 继续绑定学号。
     */
    @PostMapping("/verify-email")
    public ApiResponse<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ApiResponse.ok(authService.verifyEmail(request.email(), request.code()));
    }

    /**
     * 学号绑定并完成首次认证（一人一校，认证后不可改绑）。
     */
    @PostMapping("/bind-student-id")
    public ApiResponse<AuthTokenResponse> bindStudentId(@Valid @RequestBody BindStudentIdRequest request) {
        return ApiResponse.ok(authService.bindStudentId(request.emailToken(), request.studentId()));
    }
}
