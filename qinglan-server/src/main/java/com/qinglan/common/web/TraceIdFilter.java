package com.qinglan.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求级 traceId：读入站 {@code X-Request-Id}（有则沿用、无则生成），写入 MDC 并在响应头回传。
 *
 * <p>它排在过滤器链最前（{@link Ordered#HIGHEST_PRECEDENCE}），保证本次请求后续的所有日志
 * （含 Spring Security、各业务模块）都能带上同一个 traceId，便于跨模块串联排查。</p>
 *
 * <p>请求结束在 {@code finally} 里清理 MDC：线程池会复用线程，不清理会把上一个请求的 traceId
 * 带到下一个请求上（串号），比没有 traceId 更危险。</p>
 *
 * <p>入站取值做白名单校验（仅 {@code [A-Za-z0-9._-]}、≤64 字符）：该值会被原样写进响应头与日志，
 * 若直接采纳攻击者可控内容会造成响应头注入 / 日志伪造。</p>
 *
 * <p>由 {@code WebFiltersConfig} 以 {@code @Bean} 注册（而非 {@code @Component}）：
 * {@code @WebMvcTest} 切片会扫入 {@code Filter} 类型的 {@code @Component}，却不会带上它的依赖，
 * 导致切片测试全红；注册在 {@code @Configuration} 里则可避免切片误加载。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /** 只接受安全的 traceId 字符集，避免响应头注入与日志伪造 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            // DEBUG 级别记录一行请求起点；日志级别 pattern 会带上 MDC 里的 traceId
            log.debug("请求开始：{} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(String inbound) {
        if (inbound != null) {
            String candidate = inbound.trim();
            if (SAFE_TRACE_ID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }
}
