package com.qinglan.common.security;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

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

    /**
     * 取当前用户，未登录返回空而不抛异常。
     *
     * <p>供游客也能访问的只读接口（首页信息流、帖子详情）使用——这类接口需要区分
     * 「游客」与「已认证学生」来决定联系方式是否脱敏（不变量 #2）。
     * 需要登录的接口继续用 {@link #requireUserId()}。</p>
     */
    public static Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    /**
     * 当前访问者，未登录即游客。只读接口用它决定联系方式是否脱敏（不变量 #2）。
     */
    public static Viewer viewer() {
        return new Viewer(currentUserId().orElse(null));
    }
}
