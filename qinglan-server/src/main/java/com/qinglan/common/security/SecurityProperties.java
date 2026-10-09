package com.qinglan.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 安全相关配置（IP 限流与代理信任）。
 *
 * <p>归一化缺省值放在紧凑构造器里：记录用构造器绑定，缺失项为 null / 0 / false，
 * 统一补齐免得每个使用点判空。</p>
 *
 * @param trustedProxyHops 信任的代理跳数，默认 0（不信任任何代理，只用 {@code getRemoteAddr()}）。
 *                        见 {@link IpRateLimitFilter} 的取舍说明
 * @param rateLimit        IP 维度限流配置
 */
@ConfigurationProperties(prefix = "qinglan.security")
public record SecurityProperties(int trustedProxyHops, RateLimit rateLimit) {

    private static final int DEFAULT_MAX_REQUESTS = 20;
    private static final List<String> DEFAULT_PATHS = List.of("/api/auth/email/code");

    public SecurityProperties {
        if (trustedProxyHops < 0) {
            trustedProxyHops = 0;
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(Boolean.TRUE, DEFAULT_MAX_REQUESTS, Duration.ofMinutes(1), DEFAULT_PATHS);
        }
    }

    /**
     * @param enabled     是否启用（默认 true）
     * @param maxRequests 窗口内单 IP 允许的请求数上限（默认 20）
     * @param window      限流窗口（默认 1 分钟）
     * @param paths       受保护的路径（Ant 风格，默认仅验证码发送端点）
     */
    public record RateLimit(Boolean enabled, int maxRequests, Duration window, List<String> paths) {

        public RateLimit {
            if (enabled == null) {
                enabled = Boolean.TRUE;
            }
            if (maxRequests <= 0) {
                maxRequests = DEFAULT_MAX_REQUESTS;
            }
            if (window == null) {
                window = Duration.ofMinutes(1);
            }
            if (paths == null || paths.isEmpty()) {
                paths = DEFAULT_PATHS;
            }
        }
    }
}
