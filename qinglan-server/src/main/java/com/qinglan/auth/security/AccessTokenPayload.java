package com.qinglan.auth.security;

import com.qinglan.user.entity.UserRole;

/**
 * 访问令牌载荷。
 */
public record AccessTokenPayload(Long userId, UserRole role) {
}
