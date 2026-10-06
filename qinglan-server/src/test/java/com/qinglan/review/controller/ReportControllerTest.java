package com.qinglan.review.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.review.dto.CreateReportRequest;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 举报接口的参数校验与举报人取值（Web 层切片）。
 */
@WebMvcTest(controllers = ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    private static final long REPORTER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("缺少举报对象返回 400")
    void shouldRejectMissingTarget() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"reasonCode\":\"ABUSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("举报对象 id 非正数返回 400")
    void shouldRejectNonPositiveTargetId() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":0,\"reasonCode\":\"ABUSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("未知的目标类型返回 400，而不是 500")
    void shouldRejectUnknownTargetType() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"TOPIC\",\"targetId\":1,\"reasonCode\":\"ABUSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("合法举报返回成功，且举报人取自登录态而非请求体")
    void shouldReportSuccessfully() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(REPORTER_ID, null, List.of()));

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":1,\"reasonCode\":\"ABUSE\",\"detail\":\"人身攻击\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        ArgumentCaptor<CreateReportRequest> captor = ArgumentCaptor.forClass(CreateReportRequest.class);
        verify(reportService).report(eq(REPORTER_ID), captor.capture());
        assertThat(captor.getValue().targetId()).isEqualTo(1L);
        assertThat(captor.getValue().reasonCode()).isEqualTo(ReportReason.ABUSE);
    }
}
