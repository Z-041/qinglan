package com.qinglan.user.dto;

import com.qinglan.user.entity.UserStatus;

import java.time.Instant;

/**
 * 对外用户档案。
 *
 * <p>不变量 #3：绝不包含 email、studentId 明文或密文，管理员接口亦复用此 DTO。</p>
 */
public record UserProfileResponse(
        Long id,
        String role,
        UserStatus status,
        int creditScore,
        Instant bannedUntil,
        Instant createdAt
) {
}
