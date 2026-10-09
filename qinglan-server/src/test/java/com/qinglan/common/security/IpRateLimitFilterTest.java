package com.qinglan.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.common.store.InMemoryEphemeralStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IP 维度限流：超限 429、不同 IP 互不影响、默认不信任 X-Forwarded-For。
 *
 * <p>用 standalone MockMvc 隔离测试过滤器行为，不依赖 Spring 上下文与数据库。</p>
 */
class IpRateLimitFilterTest {

    private static final String PROBE = "/api/probe";

    @Test
    @DisplayName("同一 IP 超过上限后返回 429 QL-429-006")
    void shouldReturn429AfterLimit() throws Exception {
        MockMvc mockMvc = mockMvc(filter(0, 2, List.of(PROBE)));

        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1")).andExpect(status().isOk());
        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1")).andExpect(status().isOk());
        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QL-429-006"));
    }

    @Test
    @DisplayName("不同 IP 各用各的配额，互不影响")
    void shouldIsolateLimitPerIp() throws Exception {
        MockMvc mockMvc = mockMvc(filter(0, 1, List.of(PROBE)));

        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1")).andExpect(status().isOk());
        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1")).andExpect(status().isTooManyRequests());
        // 另一个 IP 不受前一个 IP 超限的影响
        mockMvc.perform(fromIp(get(PROBE), "10.0.0.2")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("默认不信任 XFF：伪造 X-Forwarded-For 不能绕过限流")
    void shouldIgnoreForwardedForByDefault() throws Exception {
        MockMvc mockMvc = mockMvc(filter(0, 1, List.of(PROBE)));

        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1").header("X-Forwarded-For", "1.1.1.1"))
                .andExpect(status().isOk());
        // 换一个伪造的 XFF，但来源 TCP 地址不变 → 仍应被限流
        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1").header("X-Forwarded-For", "2.2.2.2"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("显式信任 1 跳代理时，按 XFF 最后一跳区分客户端")
    void shouldTrustForwardedForWhenConfigured() throws Exception {
        MockMvc mockMvc = mockMvc(filter(1, 1, List.of(PROBE)));

        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1").header("X-Forwarded-For", "9.9.9.9"))
                .andExpect(status().isOk());
        // 不同客户端（不同 XFF）各得一份配额
        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1").header("X-Forwarded-For", "8.8.8.8"))
                .andExpect(status().isOk());
        // 与第一个相同客户端的再次请求应被限流
        mockMvc.perform(fromIp(get(PROBE), "10.0.0.1").header("X-Forwarded-For", "9.9.9.9"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("未受保护的路径不受限流影响")
    void shouldNotLimitUnprotectedPaths() throws Exception {
        MockMvc mockMvc = mockMvc(filter(0, 1, List.of(PROBE)));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(fromIp(get("/api/other"), "10.0.0.1")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("关闭限流后不再拦截")
    void shouldNotLimitWhenDisabled() throws Exception {
        SecurityProperties.RateLimit disabled =
                new SecurityProperties.RateLimit(false, 1, Duration.ofMinutes(1), List.of(PROBE));
        MockMvc mockMvc = mockMvc(new SecurityProperties(0, disabled));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(fromIp(get(PROBE), "10.0.0.1")).andExpect(status().isOk());
        }
    }

    private SecurityProperties filter(int trustedHops, int maxRequests, List<String> paths) {
        return new SecurityProperties(trustedHops,
                new SecurityProperties.RateLimit(true, maxRequests, Duration.ofMinutes(1), paths));
    }

    private MockMvc mockMvc(SecurityProperties properties) {
        IpRateLimitFilter filter = new IpRateLimitFilter(
                new InMemoryEphemeralStore(), properties, new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(new ProbeController()).addFilters(filter).build();
    }

    private MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @RestController
    static class ProbeController {

        @GetMapping(PROBE)
        String probe() {
            return "ok";
        }

        @GetMapping("/api/other")
        String other() {
            return "ok";
        }
    }
}
