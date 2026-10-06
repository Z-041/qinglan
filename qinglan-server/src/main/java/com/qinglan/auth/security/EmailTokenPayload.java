package com.qinglan.auth.security;

/**
 * 邮箱认证令牌的载荷。
 *
 * @param emailHash 校园邮箱的不可逆哈希，用于定位/创建用户
 * @param jti       令牌唯一标识，供一次性消费记录使用（防重放）
 */
public record EmailTokenPayload(String emailHash, String jti) {
}
