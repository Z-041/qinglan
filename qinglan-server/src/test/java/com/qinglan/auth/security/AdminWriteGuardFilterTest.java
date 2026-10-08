package com.qinglan.auth.security;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理端写入口的账号复查（R-31 + P5 D9）。
 *
 * <p>盯三件事：被封禁的管理员不能再执行管理写操作；<b>角色被收回/降级后旧令牌立即失效</b>
 * （否则「收回权限」要等令牌到期才生效）；读操作与非管理接口不受影响（被封禁的学生仍可浏览，
 * 见经验 #13）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminWriteGuardFilterTest {

    private static final long OPERATOR_ID = 99L;

    @Mock
    private UserService userService;

    @Mock
    private AccessDeniedHandler accessDeniedHandler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("被封禁的管理员执行管理写操作：拒绝并走统一的 403 出口，不进入后续链路")
    void shouldDenyWriteWhenOperatorUnusable() throws Exception {
        authenticateAs(OPERATOR_ID, UserRole.ADMIN);
        doThrow(new BizException(ErrorCode.USER_BANNED)).when(userService).requireUsableById(OPERATOR_ID);
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("POST", "/api/admin/posts/1/take-down"), new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isFalse();
        verify(accessDeniedHandler).handle(any(), any(), any(AccessDeniedException.class));
    }

    @Test
    @DisplayName("角色已与令牌不一致（权限被收回或降级）：旧令牌写请求立即 403，不必等令牌到期")
    void shouldDenyWriteWhenTokenRoleIsStale() throws Exception {
        // 令牌里还是 ROLE_ADMIN，但数据库里已被收回成 STUDENT
        authenticateAs(OPERATOR_ID, UserRole.ADMIN);
        when(userService.requireUsableById(OPERATOR_ID)).thenReturn(userWithRole(UserRole.STUDENT));
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("POST", "/api/admin/users/2/ban"), new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isFalse();
        verify(accessDeniedHandler).handle(any(), any(), any(AccessDeniedException.class));
    }

    @Test
    @DisplayName("降级同样生效：ADMIN → REVIEWER 后旧令牌不能再执行管理员专属写操作")
    void shouldDenyWriteWhenRoleDowngraded() throws Exception {
        authenticateAs(OPERATOR_ID, UserRole.ADMIN);
        when(userService.requireUsableById(OPERATOR_ID)).thenReturn(userWithRole(UserRole.REVIEWER));
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("POST", "/api/admin/announcements/1/publish"), new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isFalse();
        verify(accessDeniedHandler).handle(any(), any(), any(AccessDeniedException.class));
    }

    @Test
    @DisplayName("账号可用且令牌角色与数据库一致时管理写操作正常放行")
    void shouldAllowWriteWhenOperatorUsable() throws Exception {
        authenticateAs(OPERATOR_ID, UserRole.ADMIN);
        when(userService.requireUsableById(OPERATOR_ID)).thenReturn(userWithRole(UserRole.ADMIN));
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("POST", "/api/admin/users/2/ban"), new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isTrue();
        verify(userService).requireUsableById(OPERATOR_ID);
        verifyNoInteractions(accessDeniedHandler);
    }

    @Test
    @DisplayName("超管令牌 + 超管身份同样放行（角色比对不是只认管理员）")
    void shouldAllowSuperAdminWrite() throws Exception {
        authenticateAs(OPERATOR_ID, UserRole.SUPER_ADMIN);
        when(userService.requireUsableById(OPERATOR_ID)).thenReturn(userWithRole(UserRole.SUPER_ADMIN));
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("POST", "/api/admin/announcements/1/publish"), new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isTrue();
    }

    @Test
    @DisplayName("管理端读操作不复查：被封禁或被降级也应能查看（与「封禁学生仍可浏览」同口径）")
    void shouldNotGuardReadRequests() throws Exception {
        authenticateAs(OPERATOR_ID, UserRole.ADMIN);
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("GET", "/api/admin/review-queue"), new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isTrue();
        verifyNoInteractions(userService, accessDeniedHandler);
    }

    @Test
    @DisplayName("未认证的写请求不在这里判：交由授权规则统一返回 401")
    void shouldLeaveUnauthenticatedRequestToAuthorization() throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("POST", "/api/admin/posts/1/approve"), new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isTrue();
        verifyNoInteractions(userService, accessDeniedHandler);
    }

    @Test
    @DisplayName("非管理前缀的写请求不在此复查：业务入口各自带自己的校验")
    void shouldIgnoreNonAdminPaths() throws Exception {
        authenticateAs(OPERATOR_ID, UserRole.ADMIN);
        AtomicBoolean reached = new AtomicBoolean(false);

        filter().doFilter(request("POST", "/api/posts"), new MockHttpServletResponse(), chainOf(reached));

        assertThat(reached).isTrue();
        verify(userService, never()).requireUsableById(any());
        verifyNoInteractions(accessDeniedHandler);
    }

    // ------------------------------------------------------------------ 辅助

    private AdminWriteGuardFilter filter() {
        return new AdminWriteGuardFilter(userService, accessDeniedHandler);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private static FilterChain chainOf(AtomicBoolean reached) {
        return (request, response) -> reached.set(true);
    }

    private static User userWithRole(UserRole role) {
        User user = User.register("email-hash", "student-id-hash", "enc", "salt");
        user.changeRole(role);
        return user;
    }

    /** 认证态按真实过滤器写入的权威集合构造：ROLE_* + 该角色的权限点 */
    private static void authenticateAs(Long userId, UserRole role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(
                JwtAuthenticationFilter.ROLE_AUTHORITY_PREFIX + role.name()));
        role.permissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.name())));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, authorities));
    }
}
