package com.qinglan.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.common.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * IP 维度限流过滤器，优先保护验证码发送这类敏感端点。
 *
 * <p>计数落在 {@link EphemeralStore}（memory / redis 均可），键为
 * {@code ql:security:ratelimit:{ip}}，窗口内累加超限即返回 {@code 429 QL-429-006}。
 * 与业务级限流（发送间隔、上传次数、公告已读）互补：那些是「同一邮箱 / 用户」，本过滤器是
 * 「同一来源 IP」，用来挡验证码接口被撞库、骚扰。</p>
 *
 * <p><b>「信任哪一跳」的取舍（T-26① 的已知问题）</b>：{@code X-Forwarded-For} 是客户端可伪造的
 * 请求头。默认 {@code qinglan.security.trusted-proxy-hops=0}，即<b>不信任任何代理</b>，只取
 * {@code request.getRemoteAddr()}——由 TCP 连接对端地址决定，客户端无法伪造。只有当部署方
 * 明确知道前置代理层数时，才把该值设为对应跳数（如前置 1 层 nginx 则设 1），此时取 XFF 右侧
 * 第 hops 个地址为真实客户端 IP；XFF 条目不足 hops 时回退到 {@code getRemoteAddr()}，不给伪造
 * 留空子。改动该值必须先确认拓扑，配错了限流会以错误维度统计（要么被伪造绕过、要么把代理 IP
 * 当成全体用户）。</p>
 *
 * <p>由 {@code WebFiltersConfig} 以 {@code @Bean} 注册（而非 {@code @Component}），避免
 * {@code @WebMvcTest} 切片扫入本过滤器却缺其依赖而报错。</p>
 */
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitFilter.class);
    private static final String KEY_PREFIX = "ql:security:ratelimit:";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final EphemeralStore ephemeralStore;
    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public IpRateLimitFilter(EphemeralStore ephemeralStore,
                             SecurityProperties properties,
                             ObjectMapper objectMapper) {
        this.ephemeralStore = ephemeralStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        SecurityProperties.RateLimit rateLimit = properties.rateLimit();
        if (!Boolean.TRUE.equals(rateLimit.enabled()) || !isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        long count = ephemeralStore.increment(KEY_PREFIX + clientIp, rateLimit.window());
        if (count > rateLimit.maxRequests()) {
            log.warn("IP 限流触发：ip={} 路径={} 窗口内第 {} 次请求，上限 {}",
                    clientIp, request.getRequestURI(), count, rateLimit.maxRequests());
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String pattern : properties.rateLimit().paths()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private String resolveClientIp(HttpServletRequest request) {
        int trustedHops = properties.trustedProxyHops();
        if (trustedHops <= 0) {
            // 默认：不信任 XFF，只用 TCP 对端地址（客户端无法伪造）
            return request.getRemoteAddr();
        }
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        List<String> chain = splitForwardedFor(forwardedFor);
        if (chain.size() >= trustedHops) {
            return chain.get(chain.size() - trustedHops);
        }
        return request.getRemoteAddr();
    }

    private List<String> splitForwardedFor(String forwardedFor) {
        List<String> chain = new ArrayList<>();
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return chain;
        }
        for (String hop : forwardedFor.split(",")) {
            String trimmed = hop.trim();
            if (!trimmed.isEmpty()) {
                chain.add(trimmed);
            }
        }
        return chain;
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.RATE_LIMITED;
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode.code(), errorCode.message()));
    }
}
