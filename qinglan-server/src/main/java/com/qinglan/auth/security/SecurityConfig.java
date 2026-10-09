package com.qinglan.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全骨架：无状态 Bearer 认证 + 方法级授权。
 *
 * <p>CSRF 说明：本服务不使用 Cookie/Session 会话，令牌由前端显式随请求头携带，
 * 浏览器不会自动附带凭证，因此不存在 CSRF 攻击面，故关闭 CSRF 保护；
 * 一旦将来引入 Cookie 会话，必须重新开启。</p>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // 本服务只返回 JSON 与图片，页面本身由前端静态托管，故 CSP 取最严格的 'none'：
                        // 任何被注入的内容都无法再加载子资源。若将来改由后端直接托管 SPA 页面，
                        // 必须放宽本策略，否则页面自身的样式与脚本会被拦截。
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"))
                        // CSP 的 frame-ancestors 在旧浏览器上不生效，补 X-Frame-Options 做双保险
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 容器编排的探活需要匿名可读：只放行 health（含 liveness/readiness 子路径），
                        // 其余 actuator 端点既未在 exposure 中开放（不存在映射），也会被 anyRequest 拦成 401
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 「我的帖子」需登录：它匹配 /api/posts/* 这一通配（单段），必须先在放行之前拦截，
                        // 否则会被当成游客可读的信息流详情放行
                        .requestMatchers(HttpMethod.GET, "/api/posts/mine").authenticated()
                        // 游客是只读角色（设计方案第二章），首页信息流与帖子详情需对未登录开放。
                        // 只放行这几个具体的 GET 路径，不用 /api/posts/** 通配，避免误放行写操作或管理接口
                        .requestMatchers(HttpMethod.GET,
                                "/api/topics",
                                "/api/posts",
                                "/api/posts/*",
                                "/api/posts/*/comments").permitAll()
                        // 上传的图片由受控端点下发（UploadedImageController，接管 /uploads/**）：
                        // 游客必须能看**已通过帖**的图，故这里放行；可见性由端点按 Post.canBeViewedBy
                        // 判定（浏览器 <img> 不带令牌，等同于游客），不可见与不存在一律 404。
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // 公告：游客也要能读与记已读（计划 D4/D5）。GET 放行待读与历史；
                        // POST 仅放行单个已读标记——只写一条已读记录、有唯一键幂等、不落任何身份明文。
                        .requestMatchers(HttpMethod.GET, "/api/announcements", "/api/announcements/pending").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/announcements/*/read").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 紧跟在认证过滤器之后：此时才能拿到 principal，而角色又从令牌静态得来，
                // 所以管理端写操作必须在这里复查一次账号是否仍然可用（被封禁的管理员不得继续处置）
                .addFilterAfter(new AdminWriteGuardFilter(userService, accessDeniedHandler()),
                        JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeError(response, ErrorCode.UNAUTHENTICATED);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeError(response, ErrorCode.ACCESS_DENIED);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode.code(), errorCode.message()));
    }
}
