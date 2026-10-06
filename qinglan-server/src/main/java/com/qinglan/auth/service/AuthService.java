package com.qinglan.auth.service;

import com.qinglan.auth.dto.AuthTokenResponse;
import com.qinglan.auth.dto.SendCodeResponse;
import com.qinglan.auth.dto.VerifyEmailResponse;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.user.entity.User;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 认证编排：邮箱验证码 → （老用户）登录 / （新用户）学号绑定。
 *
 * <p>跨模块调用说明：本服务调用 {@code user.UserService}，方向 {@code auth → user} 已列入
 * AGENTS.md 第三章白名单。身份字段的加解密与用户创建集中在 user 模块内，本模块不直接
 * 触碰 {@code UserRepository}，也不持有身份明文。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmailVerificationService emailVerificationService;
    private final UserService userService;
    private final JwtService jwtService;

    public SendCodeResponse sendEmailCode(String email) {
        return emailVerificationService.sendCode(email);
    }

    public VerifyEmailResponse verifyEmail(String email, String code) {
        String emailHash = emailVerificationService.verifyCode(email, code);
        Optional<User> existing = userService.findByEmailHash(emailHash);
        if (existing.isPresent()) {
            User user = existing.get();
            userService.assertUsable(user);
            return new VerifyEmailResponse(
                    jwtService.issueAccessToken(user.getId(), user.getRole()),
                    null,
                    false,
                    userService.toProfile(user)
            );
        }
        return new VerifyEmailResponse(null, jwtService.issueEmailToken(emailHash), true, null);
    }

    public AuthTokenResponse bindStudentId(String emailToken, String studentId) {
        String emailHash = jwtService.parseEmailToken(emailToken)
                .orElseThrow(() -> new BizException(ErrorCode.EMAIL_TOKEN_INVALID));
        User user = userService.createVerifiedStudent(emailHash, studentId);
        return new AuthTokenResponse(
                jwtService.issueAccessToken(user.getId(), user.getRole()),
                "Bearer",
                jwtService.accessTokenTtlSeconds(),
                userService.toProfile(user)
        );
    }
}
