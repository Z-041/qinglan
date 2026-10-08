package com.qinglan.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 从 Authorization: Bearer 解析访问令牌并填充 SecurityContext。
 *
 * <p>令牌无效时不抛异常、不拦截请求，交由后续授权规则统一返回 401。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** 角色权威前缀。管理端写入口复查（AdminWriteGuardFilter）也用它比对令牌里的角色 */
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtService::parseAccessToken)
                .ifPresent(payload -> authenticate(request, payload));
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }

    private void authenticate(HttpServletRequest request, AccessTokenPayload payload) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(
                ROLE_AUTHORITY_PREFIX + payload.role().name().toUpperCase(Locale.ROOT)));
        // 权限点由角色派生后在这里展开，**不写进令牌**（P5 设计 4.4 的偏差，已回写方案）：
        // 令牌本来就带 role，再存一份权限点既冗余，又会让「矩阵改了、旧令牌仍按旧矩阵放行」
        // 成为可能。角色 → 权限点只有 UserRole#permissions 一处映射。
        payload.role().permissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.name())));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                payload.userId(), null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
