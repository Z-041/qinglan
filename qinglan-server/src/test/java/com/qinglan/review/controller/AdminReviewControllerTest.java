package com.qinglan.review.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.TakeDownPostRequest;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.service.AppealService;
import com.qinglan.review.service.ReviewQueueService;
import com.qinglan.review.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审核工作台接口的参数校验与操作人取值（Web 层切片）。
 *
 * <p>权限判定（学生 403、管理员放行）由 {@code SecuritySkeletonTest} 在完整上下文中验证，
 * 此处关闭过滤器只校验 DTO 约束、状态码与「操作人取自登录态」。</p>
 */
@WebMvcTest(controllers = AdminReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminReviewControllerTest {

    private static final long OPERATOR_ID = 99L;
    private static final long POST_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private ReviewQueueService reviewQueueService;

    @MockitoBean
    private AppealService appealService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        OPERATOR_ID, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ------------------------------------------------------------------ 队列

    @Test
    @DisplayName("队列返回游标分页，且 nextCursor 是字符串（前端原样回传）")
    void shouldReturnQueueWithStringCursor() throws Exception {
        when(reviewQueueService.queue("2:1000:1")).thenReturn(CursorPage.of(
                List.of(new ReviewQueueItemResponse(ReviewTargetType.POST, POST_ID, "摘要", "APPROVED",
                        2, Instant.parse("2026-10-06T00:00:00Z"), List.of(ReportReason.ABUSE))),
                "3:2000:1"));

        mockMvc.perform(get("/api/admin/review-queue").param("cursor", "2:1000:1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(POST_ID))
                .andExpect(jsonPath("$.data.items[0].reportCount").value(2))
                .andExpect(jsonPath("$.data.nextCursor").value("3:2000:1"));
    }

    @Test
    @DisplayName("不带游标即取首页，游标参数对服务层为空")
    void shouldRequestFirstPageWhenCursorAbsent() throws Exception {
        when(reviewQueueService.queue(null)).thenReturn(CursorPage.empty());

        mockMvc.perform(get("/api/admin/review-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(reviewQueueService).queue(null);
    }

    // ------------------------------------------------------------------ 处置

    @Test
    @DisplayName("下架缺少违规类型返回 400")
    void shouldRejectTakeDownWithoutReasonCode() throws Exception {
        mockMvc.perform(post("/api/admin/posts/{postId}/take-down", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detail\":\"说明\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("下架封禁天数超出上限返回 400")
    void shouldRejectTakeDownWithExcessiveBanDays() throws Exception {
        mockMvc.perform(post("/api/admin/posts/{postId}/take-down", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"ABUSE\",\"banDays\":3651}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("合法下架调用服务，且操作人取自登录态而非请求体")
    void shouldTakeDownWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/posts/{postId}/take-down", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"ABUSE\",\"detail\":\"人身攻击\",\"banDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        ArgumentCaptor<TakeDownPostRequest> captor = ArgumentCaptor.forClass(TakeDownPostRequest.class);
        verify(reviewService).takeDown(eq(POST_ID), eq(OPERATOR_ID), captor.capture());
        assertThat(captor.getValue().reasonCode()).isEqualTo(ReportReason.ABUSE);
        assertThat(captor.getValue().banDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("驳回理由为空返回 400")
    void shouldRejectBlankRejectReason() throws Exception {
        mockMvc.perform(post("/api/admin/posts/{postId}/reject", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("放行不带请求体，操作人取自登录态")
    void shouldApproveWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/posts/{postId}/approve", POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(reviewService).approve(POST_ID, OPERATOR_ID);
    }

    @Test
    @DisplayName("驳回举报缺少目标 id 返回 400")
    void shouldRejectDismissWithoutTargetId() throws Exception {
        mockMvc.perform(post("/api/admin/reports/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"reason\":\"内容合规\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    // ------------------------------------------------------------------ 申诉复审

    @Test
    @DisplayName("复审通过调用服务，操作人取自登录态")
    void shouldAcceptAppealWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/appeals/{appealId}/accept", 500L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"确系误判\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(appealService).accept(500L, OPERATOR_ID, "确系误判");
    }

    @Test
    @DisplayName("复审不通过调用服务，操作人取自登录态")
    void shouldRejectAppealWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/appeals/{appealId}/reject", 500L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"维持原判\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(appealService).reject(500L, OPERATOR_ID, "维持原判");
    }

    @Test
    @DisplayName("复审理由为空返回 400")
    void shouldRejectBlankAppealReason() throws Exception {
        mockMvc.perform(post("/api/admin/appeals/{appealId}/accept", 500L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }
}
