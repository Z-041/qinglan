package com.qinglan.auth.security;

import com.qinglan.common.error.BizException;
import com.qinglan.common.security.CurrentUser;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理端<b>写操作</b>的账号复查：账号是否可用 + 令牌里的角色是否仍是数据库里的角色。
 *
 * <p>为什么必须有这一层：访问令牌是无状态的，角色声明在签发时就固定了。{@code UserService}
 * 已经写明「封禁不可能靠登录时的检查兜住，因此每个写入口都要重新校验一次」，业务模块
 * （发帖 / 评论 / 举报 / 申诉 / 上传）都照做了，但管理端点没有——于是被封禁的管理员在令牌
 * 剩余有效期（默认 2h）内仍能封禁他人、下架内容、发布公告。</p>
 *
 * <p>角色比对是 P5「权限收回立即生效」的落点（决策 D9）：超管把某个管理员降级或收回后，
 * 旧令牌在到期前仍带着 {@code ROLE_ADMIN}，若只查「账号可用」而不比对角色，被收回者照样能
 * 写完 2 小时——「收回权限」就只是心理安慰。角色 → 权限点只有一处映射，因此比对角色即可；
 * 比对一次主键查询，代价可忽略。</p>
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
                        new AccessDeniedException("账号不可用或权限已变更，请重新登录"));
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
            User operator = userService.requireUsableById(operatorId);
            return tokenRoleMatches(operator.getRole());
        } catch (BizException ex) {
            // 具体原因（封禁 / 黑名单 / 账号不存在）不回显：与安全红线「错误信息不回显内部细节」一致
            return false;
        }
    }

    /**
     * 令牌里的角色是否等于数据库里的当前角色。
     *
     * <p>令牌角色从授权集合里的 {@code ROLE_*} 权威读取（认证过滤器写入的就是这一个），
     * 而不是再解析一次令牌——这里已经处在认证之后。</p>
     */
    private boolean tokenRoleMatches(UserRole currentRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String expected = JwtAuthenticationFilter.ROLE_AUTHORITY_PREFIX + currentRole.name();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(expected::equals);
    }
}
