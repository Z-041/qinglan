package com.qinglan.notification.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.web.CursorPage;
import com.qinglan.notification.dto.NotificationItemResponse;
import com.qinglan.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 消息中心接口的登录态与状态码（Web 层切片）。
 *
 * <p>关闭过滤器后权限由 {@code SecurityConfig} 在完整上下文里验证；此处只校验
 * 「未登录 → requireUserId 抛 401」与「已登录 → 200」这条边界。</p>
 */
@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("未登录访问消息中心返回 401")
    void shouldRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("已登录列出通知返回 200，数据取自当前用户")
    void shouldListNotifications() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(USER_ID, null, List.of()));
        when(notificationService.list(USER_ID, null)).thenReturn(CursorPage.of(
                List.of(new NotificationItemResponse(5L, NotificationType.COMMENT, "有人评论了你的帖子",
                        "同问", "/posts/1", null, Instant.parse("2026-10-07T00:00:00Z"))),
                "5"));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].id").value(5))
                .andExpect(jsonPath("$.data.nextCursor").value("5"));
    }
}
