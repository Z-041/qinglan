package com.qinglan.announcement.controller;

import com.qinglan.announcement.dto.AnnouncementSummaryResponse;
import com.qinglan.announcement.dto.PendingAnnouncementResponse;
import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.announcement.service.AnnouncementService;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生端公告接口（Web 层切片）。
 *
 * <p>游客与登录用户都可访问：待读查询无未读时 data 为 null；标记已读匿名可写（游客也要能被强制阅读），
 * 此处只校验参数约束、匿名写人取值与服务层透传的状态码。</p>
 */
@WebMvcTest(controllers = AnnouncementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnnouncementControllerTest {

    private static final long ANNOUNCEMENT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnnouncementService announcementService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无未读公告时待读接口返回 200，data 为 null")
    void shouldReturnNullWhenNoPending() throws Exception {
        when(announcementService.findPending(isNull(), isNull())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/announcements/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("有未读公告时待读接口返回公告内容")
    void shouldReturnPendingAnnouncement() throws Exception {
        when(announcementService.findPending(isNull(), eq("dev-1"))).thenReturn(Optional.of(
                new PendingAnnouncementResponse(9L, "紧急通知", "请先阅读", AnnouncementLevel.URGENT,
                        5, true, true, Instant.parse("2026-10-07T00:00:00Z"))));

        mockMvc.perform(get("/api/announcements/pending").param("deviceId", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.level").value("URGENT"))
                .andExpect(jsonPath("$.data.minReadSeconds").value(5));
    }

    @Test
    @DisplayName("游客标记已读成功，userId 取自登录态（游客为 null）")
    void shouldMarkReadAsGuest() throws Exception {
        mockMvc.perform(post("/api/announcements/" + ANNOUNCEMENT_ID + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"dev-1\",\"durationMs\":6000,"
                                + "\"scrolledToEnd\":true,\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(announcementService).markRead(eq(ANNOUNCEMENT_ID), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("登录用户标记已读时把 userId 透传给服务层")
    void shouldMarkReadAsUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));

        mockMvc.perform(post("/api/announcements/" + ANNOUNCEMENT_ID + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMs\":6000,\"scrolledToEnd\":true,\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(announcementService).markRead(eq(ANNOUNCEMENT_ID), eq(7L), isNull(), any());
    }

    @Test
    @DisplayName("停留时长非正返回 400，并给出校验消息")
    void shouldRejectNonPositiveDuration() throws Exception {
        mockMvc.perform(post("/api/announcements/" + ANNOUNCEMENT_ID + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMs\":0,\"scrolledToEnd\":true,\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"))
                .andExpect(jsonPath("$.message").value("停留时长必须为正"));
    }

    @Test
    @DisplayName("阅读时长不足返回 400（不变量 #6）")
    void shouldReturn400WhenReadTooEarly() throws Exception {
        doThrow(new BizException(ErrorCode.ANNOUNCEMENT_READ_TOO_EARLY))
                .when(announcementService).markRead(eq(ANNOUNCEMENT_ID), any(), any(), any());

        mockMvc.perform(post("/api/announcements/" + ANNOUNCEMENT_ID + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"dev-1\",\"durationMs\":1000,"
                                + "\"scrolledToEnd\":true,\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-011"));
    }

    @Test
    @DisplayName("公告不存在返回 404")
    void shouldReturn404WhenAnnouncementMissing() throws Exception {
        doThrow(new BizException(ErrorCode.ANNOUNCEMENT_NOT_FOUND))
                .when(announcementService).markRead(eq(ANNOUNCEMENT_ID), any(), any(), any());

        mockMvc.perform(post("/api/announcements/" + ANNOUNCEMENT_ID + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"dev-1\",\"durationMs\":6000,"
                                + "\"scrolledToEnd\":true,\"confirmed\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-007"));
    }

    @Test
    @DisplayName("公告历史返回 200 游标页")
    void shouldListHistory() throws Exception {
        when(announcementService.history(isNull(), isNull(), isNull())).thenReturn(CursorPage.of(
                List.of(new AnnouncementSummaryResponse(5L, "标题", "正文", AnnouncementLevel.NORMAL,
                        Instant.parse("2026-10-07T00:00:00Z"), false)),
                "5"));

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].id").value(5))
                .andExpect(jsonPath("$.data.nextCursor").value("5"));
    }
}
