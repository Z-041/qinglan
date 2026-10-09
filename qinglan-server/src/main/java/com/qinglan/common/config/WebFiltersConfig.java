package com.qinglan.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.common.security.IpRateLimitFilter;
import com.qinglan.common.security.SecurityProperties;
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.common.web.TraceIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 过滤器注册。
 *
 * <p>以 {@code @Bean} 而非 {@code @Component} 暴露过滤器：{@code @WebMvcTest} 切片会扫入
 * {@code Filter} 类型的 {@code @Component}，但不会为它准备依赖（{@link EphemeralStore} /
 * {@link SecurityProperties} 不在切片里），会导致所有切片测试上下文加载失败。放到
 * {@code @Configuration} 里注册即可让切片不加载它们，同时完整应用仍照常启用。</p>
 */
@Configuration
public class WebFiltersConfig {

    /** 请求级 traceId：排最前，保证后续所有日志都带上同一 traceId */
    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /** IP 维度限流：默认排在 Spring Security 之后，429 响应仍带安全头 */
    @Bean
    public IpRateLimitFilter ipRateLimitFilter(EphemeralStore ephemeralStore,
                                               SecurityProperties securityProperties,
                                               ObjectMapper objectMapper) {
        return new IpRateLimitFilter(ephemeralStore, securityProperties, objectMapper);
    }
}
