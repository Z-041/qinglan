package com.qinglan.review.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.review.service.AppealService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 申诉接口的参数校验与申诉人取值（Web 层切片）。
 */
@WebMvcTest(controllers = AppealController.class)
@AutoConfigureMockMvc(addFilters = false)
class AppealControllerTest {

    private static final long APPELLANT_ID = 100L;
    private static final long POST_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppealService appealService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("缺少申诉帖子返回 400")
    void shouldRejectMissingPostId() throws Exception {
        mockMvc.perform(post("/api/appeals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"误判\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("申诉理由为空返回 400")
    void shouldRejectBlankReason() throws Exception {
        mockMvc.perform(post("/api/appeals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":1,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("合法申诉返回成功，且申诉人取自登录态而非请求体")
    void shouldSubmitAppealWithAppellantFromPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(APPELLANT_ID, null, List.of()));

        mockMvc.perform(post("/api/appeals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":1,\"reason\":\"内容并未违规\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(appealService).submit(APPELLANT_ID, POST_ID, "内容并未违规");
    }
}
