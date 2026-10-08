package com.qinglan.auth.security;

import com.qinglan.common.security.AdminPermission;
import com.qinglan.user.entity.UserRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 认证过滤器把「角色」与「权限点」一起写进授权集合（P5 设计 4.4）。
 *
 * <p>权限点从角色派生后在这里展开，而不是写进令牌：这条测试同时钉住两件事——
 * 有权限点（否则所有端点都会被 403 拦住），且权限点严格等于该角色的矩阵（不放大也不缩小）。</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "a.b.c";

    @Mock
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("评议员令牌：ROLE_REVIEWER + 评议员的权限点，一个不多一个不少")
    void shouldExpandReviewerPermissions() throws Exception {
        authenticate(UserRole.REVIEWER);

        Set<String> authorities = authorities();

        assertThat(authorities).contains(JwtAuthenticationFilter.ROLE_AUTHORITY_PREFIX + "REVIEWER");
        assertThat(authorities).containsAll(Set.of(
                AdminPermission.MODERATION_VIEW.name(),
                AdminPermission.POST_REVIEW.name(),
                AdminPermission.REPORT_DISMISS.name(),
                AdminPermission.PROPOSAL_CREATE.name(),
                AdminPermission.PROPOSAL_VOTE.name(),
                AdminPermission.STATS_VIEW.name()));
        // 评议员不该有的：管人、复审、发公告、下放、裁决、看审计
        assertThat(authorities).doesNotContain(
                AdminPermission.USER_MANAGE.name(),
                AdminPermission.APPEAL_REVIEW.name(),
                AdminPermission.ANNOUNCEMENT_MANAGE.name(),
                AdminPermission.ROLE_GRANT.name(),
                AdminPermission.PROPOSAL_ARBITRATE.name(),
                AdminPermission.AUDIT_READ.name());
    }

    @Test
    @DisplayName("学生令牌：只有 ROLE_STUDENT，没有任何管理端权限点")
    void shouldNotGrantStudentAnyPermission() throws Exception {
        authenticate(UserRole.STUDENT);

        Set<String> authorities = authorities();

        assertThat(authorities).containsExactly(JwtAuthenticationFilter.ROLE_AUTHORITY_PREFIX + "STUDENT");
    }

    @Test
    @DisplayName("超管令牌：权限点取全集（新增权限点时不会漏给超管）")
    void shouldGrantSuperAdminEveryPermission() throws Exception {
        authenticate(UserRole.SUPER_ADMIN);

        assertThat(authorities()).containsAll(
                java.util.Arrays.stream(AdminPermission.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("没有 Authorization 头时不认证，请求继续走（由授权规则返回 401）")
    void shouldNotAuthenticateWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        AtomicBoolean reached = new AtomicBoolean(false);

        new JwtAuthenticationFilter(jwtService).doFilter(request, new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("令牌无法解析时不认证，也不抛错")
    void shouldNotAuthenticateWithMalformedToken() throws Exception {
        when(jwtService.parseAccessToken("bad")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer bad");
        AtomicBoolean reached = new AtomicBoolean(false);

        new JwtAuthenticationFilter(jwtService).doFilter(request, new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void authenticate(UserRole role) throws Exception {
        when(jwtService.parseAccessToken(TOKEN))
                .thenReturn(Optional.of(new AccessTokenPayload(99L, role)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        AtomicBoolean reached = new AtomicBoolean(false);

        new JwtAuthenticationFilter(jwtService).doFilter(request, new MockHttpServletResponse(),
                chainOf(reached));

        assertThat(reached).isTrue();
    }

    private static Set<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static FilterChain chainOf(AtomicBoolean reached) {
        return (request, response) -> reached.set(true);
    }
}
