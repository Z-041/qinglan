package com.qinglan.auth.controller;

import com.qinglan.auth.dto.SendCodeResponse;
import com.qinglan.auth.security.JwtService;
import com.qinglan.auth.service.AuthService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口的参数校验与状态码映射（Web 层切片）。
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("邮箱格式非法返回 400 与统一错误码")
    void shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("学号格式非法返回 400")
    void shouldRejectInvalidStudentId() throws Exception {
        mockMvc.perform(post("/api/auth/bind-student-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailToken\":\"token\",\"studentId\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("验证码位数非法返回 400")
    void shouldRejectInvalidCodeShape() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@qinglan.edu.cn\",\"code\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("发送验证码成功返回有效期")
    void shouldReturnExpiryOnSendSuccess() throws Exception {
        when(authService.sendEmailCode(anyString())).thenReturn(new SendCodeResponse(300, null));

        mockMvc.perform(post("/api/auth/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@qinglan.edu.cn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
    }

    @Test
    @DisplayName("业务异常映射为对应 HTTP 状态与错误码")
    void shouldMapBizException() throws Exception {
        when(authService.verifyEmail(anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.VERIFICATION_CODE_INVALID));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@qinglan.edu.cn\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-003"));
    }
}
