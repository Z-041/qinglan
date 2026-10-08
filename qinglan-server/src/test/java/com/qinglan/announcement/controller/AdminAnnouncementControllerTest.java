package com.qinglan.announcement.controller;

import com.qinglan.announcement.dto.AdminAnnouncementItemResponse;
import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.announcement.entity.AnnouncementStatus;
import com.qinglan.announcement.service.AnnouncementService;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.web.CursorPage;
import com.qinglan.user.entity.UserRole;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端公告接口的参数校验与状态码映射（Web 层切片）。
 *
 * <p>权限判定（普通管理员不可发布、学生 403）由 {@code SecuritySkeletonTest} 在完整上下文中验证，
 * 此处关闭过滤器只校验 DTO 约束与状态码。</p>
 */
@WebMvcTest(controllers = AdminAnnouncementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAnnouncementControllerTest {

    private static final long OPERATOR_ID = 99L;

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

    /** 认证态按真实过滤器写入的权威集合构造：ROLE_* + 该角色的权限点（P5 权限模型） */
    private void authenticateAsSuperAdmin() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        UserRole.SUPER_ADMIN.permissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.name())));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(OPERATOR_ID, null, authorities));
    }

    @Test
    @DisplayName("缺少标题返回 400")
    void shouldRejectMissingTitle() throws Exception {
        authenticateAsSuperAdmin();

        mockMvc.perform(post("/api/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"正文\",\"level\":\"URGENT\",\"startAt\":\"2026-10-07T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("缺少级别返回 400")
    void shouldRejectMissingLevel() throws Exception {
        authenticateAsSuperAdmin();

        mockMvc.perform(post("/api/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"紧急公告\",\"content\":\"正文\",\"startAt\":\"2026-10-07T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("最少阅读秒数超上限返回 400")
    void shouldRejectExcessiveMinReadSeconds() throws Exception {
        authenticateAsSuperAdmin();

        mockMvc.perform(post("/api/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"紧急公告\",\"content\":\"正文\",\"level\":\"URGENT\","
                                + "\"startAt\":\"2026-10-07T00:00:00Z\",\"minReadSeconds\":61}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("结束时间早于开始时间返回 400：否则公告发布后永远不生效（R-32）")
    void shouldRejectEndAtBeforeStartAt() throws Exception {
        authenticateAsSuperAdmin();

        mockMvc.perform(post("/api/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"紧急公告\",\"content\":\"正文\",\"level\":\"URGENT\","
                                + "\"startAt\":\"2026-10-07T00:00:00Z\",\"endAt\":\"2026-10-06T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("合法建草稿返回 200，且操作人取自登录态")
    void shouldCreateDraftSuccessfully() throws Exception {
        authenticateAsSuperAdmin();
        when(announcementService.createDraft(any(), any())).thenReturn(12L);
        when(announcementService.adminItem(12L)).thenReturn(new AdminAnnouncementItemResponse(
                12L, "紧急公告", AnnouncementLevel.URGENT, AnnouncementStatus.DRAFT,
                Instant.parse("2026-10-07T00:00:00Z"), null, 5, false, false, null, null,
                Instant.parse("2026-10-07T00:00:00Z")));

        mockMvc.perform(post("/api/admin/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"紧急公告\",\"content\":\"正文\",\"level\":\"URGENT\","
                                + "\"startAt\":\"2026-10-07T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        ArgumentCaptor<com.qinglan.announcement.dto.CreateAnnouncementRequest> captor =
                ArgumentCaptor.forClass(com.qinglan.announcement.dto.CreateAnnouncementRequest.class);
        org.mockito.Mockito.verify(announcementService).createDraft(eq(OPERATOR_ID), captor.capture());
        assertThat(captor.getValue().level()).isEqualTo(AnnouncementLevel.URGENT);
    }

    @Test
    @DisplayName("公告列表返回游标页")
    void shouldListAnnouncements() throws Exception {
        when(announcementService.adminList(null)).thenReturn(CursorPage.of(
                List.of(new AdminAnnouncementItemResponse(
                        5L, "标题", AnnouncementLevel.NORMAL, AnnouncementStatus.PUBLISHED,
                        Instant.parse("2026-10-07T00:00:00Z"), null, 5, false, false,
                        Instant.parse("2026-10-07T00:00:00Z"), null, Instant.parse("2026-10-07T00:00:00Z"))),
                "5"));

        mockMvc.perform(get("/api/admin/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].id").value(5))
                .andExpect(jsonPath("$.data.nextCursor").value("5"));
    }
}
