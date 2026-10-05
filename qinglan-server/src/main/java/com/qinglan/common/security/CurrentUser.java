package com.qinglan.common.security;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前请求用户工具。
 *
 * <p>认证过滤器把 userId 作为 SecurityContext 的 principal，各业务模块通过本工具取用，
 * 无需依赖 auth 模块的具体类型，保持模块单向解耦。</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
