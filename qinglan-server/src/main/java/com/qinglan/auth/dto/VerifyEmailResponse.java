package com.qinglan.auth.dto;

import com.qinglan.user.dto.UserProfileResponse;

/**
 * 邮箱验证结果。
 *
 * <p>老用户直接拿到 accessToken 完成登录；新用户拿到 emailToken（短时凭证）继续绑定学号，
 * 此时 needsStudentBinding=true 且 user 为 null。</p>
 */
public record VerifyEmailResponse(
        String accessToken,
        String emailToken,
        boolean needsStudentBinding,
        UserProfileResponse user
) {
}
