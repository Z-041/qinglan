package com.qinglan.auth.security;

import com.qinglan.common.error.BizException;
import com.qinglan.common.security.CurrentUser;
import com.qinglan.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理端<b>写操作</b>的账号可用性复查。
 *
 * <p>为什么必须有这一层：访问令牌是无状态的，角色声明在签发时就固定了。{@code UserService}
 * 已经写明「封禁不可能靠登录时的检查兜住，因此每个写入口都要重新校验一次」，业务模块
 * （发帖 / 评论 / 举报 / 申诉 / 上传）都照做了，但管理端点没有——于是被封禁的管理员在令牌
 * 剩余有效期（默认 2h）内仍能封禁他人、下架内容、发布公告，权限撤回存在一个最长 2 小时的窗口。</p>
 *
 * <p>为什么只拦写：与「被封禁的学生仍可浏览」同一口径（经验 #13：账号级禁令在每个<b>写</b>入口
 * 重新判定，只读入口放行以保留浏览能力）。管理端读接口（看板、队列列表）保持可用。</p>
 *
 * <p>输出复用 SecurityConfig 的 {@code AccessDeniedHandler}，与 {@code @PreAuthorize} 拒绝时
 * 的响应体完全一致（过滤器里抛出的 {@code BizException} 不会被 {@code @ControllerAdvice} 接住，
 * 所以这里不抛业务异常，而是直接走拒绝处理器）。</p>
 */
public class AdminWriteGuardFilter extends OncePerRequestFilter {

    /** 管理端接口前缀（注意：不是前端页面的 /admin/，页面由前端静态托管） */
    private static final String ADMIN_API_PREFIX = "/api/admin/";

    private final UserService userService;
    private final AccessDeniedHandler accessDeniedHandler;

    public AdminWriteGuardFilter(UserService userService, AccessDeniedHandler accessDeniedHandler) {
        this.userService = userService;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (requiresUsableOperator(request)) {
            Long operatorId = CurrentUser.currentUserId().orElse(null);
            // 未认证的写请求不在这里处理：交给授权规则返回 401，避免同一件事两种出口
            if (operatorId != null && !isUsable(operatorId)) {
                accessDeniedHandler.handle(request, response,
                        new AccessDeniedException("账号不可用，管理权限已失效"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean requiresUsableOperator(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith(ADMIN_API_PREFIX)) {
            return false;
        }
        String method = request.getMethod();
        return !HttpMethod.GET.matches(method)
                && !HttpMethod.HEAD.matches(method)
                && !HttpMethod.OPTIONS.matches(method);
    }

    private boolean isUsable(Long operatorId) {
        try {
            userService.requireUsableById(operatorId);
            return true;
        } catch (BizException ex) {
            // 具体原因（封禁 / 黑名单 / 账号不存在）不回显：与安全红线「错误信息不回显内部细节」一致
            return false;
        }
    }
}
