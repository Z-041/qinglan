package com.qinglan.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 限流在完整 Spring 上下文中的接线验证：真实的验证码端点在超限后返回 429 QL-429-006。
 *
 * <p>把上限调到 2，用不同邮箱（绕开发送间隔限制）来自同一 IP 连打 3 次，第 3 次应被 IP 限流拦下。</p>
 */
@SpringBootTest(properties = "qinglan.security.rate-limit.max-requests=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IpRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("同一 IP 连续发送验证码超过上限后返回 429 QL-429-006")
    void shouldRateLimitVerificationCodeEndpoint() throws Exception {
        mockMvc.perform(sendCode("a1@qinglan.edu.cn")).andExpect(status().isOk());
        mockMvc.perform(sendCode("a2@qinglan.edu.cn")).andExpect(status().isOk());
        mockMvc.perform(sendCode("a3@qinglan.edu.cn"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QL-429-006"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sendCode(String email) {
        return post("/api/auth/email/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}");
    }
}
