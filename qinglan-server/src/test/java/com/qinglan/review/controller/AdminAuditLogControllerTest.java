package com.qinglan.review.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAuditLogItemResponse;
import com.qinglan.review.service.AuditLogQueryService;
import com.qinglan.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计日志查询接口的 Web 层切片（P5 设计 7.5）。
 *
 * <p>权限点判定（管理员 403、超管 200）由 {@code SecuritySkeletonTest} 在完整上下文中验证，
 * 此处只校验参数解析、透传与出参形状。</p>
 */
@WebMvcTest(controllers = AdminAuditLogController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAuditLogControllerTest {

    private static final long OPERATOR_ID = 99L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 导出端点取当前操作人，切片里手工写入登录态（超管权限集） */
    private void authenticateAsSuperAdmin() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        UserRole.SUPER_ADMIN.permissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.name())));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(OPERATOR_ID, null, authorities));
    }

    @Test
    @DisplayName("导出：Content-Type 为 CSV、带时间戳文件名，操作人取自登录态")
    void shouldExportCsv() throws Exception {
        authenticateAsSuperAdmin();

        mockMvc.perform(get("/api/admin/audit-logs/export").param("action", "BAN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment; filename=\"audit-logs-")));

        verify(auditLogQueryService).exportCsv(eq(AuditAction.BAN), isNull(), isNull(), eq(OPERATOR_ID), any());
    }

    @Test
    @DisplayName("导出超限：返回专用错误码 400（不静默截断）")
    void shouldReturnErrorWhenExportOverLimit() throws Exception {
        authenticateAsSuperAdmin();
        doThrow(new BizException(ErrorCode.EXPORT_ROW_LIMIT_EXCEEDED))
                .when(auditLogQueryService).requireExportable(any(), any(), any());

        mockMvc.perform(get("/api/admin/audit-logs/export"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-014"));
    }

    @Test
    @DisplayName("不带筛选返回一页，出参含动作与操作人但不含任何身份字段")
    void shouldReturnPage() throws Exception {
        when(auditLogQueryService.page(null, null, null, null)).thenReturn(CursorPage.of(
                List.of(new AdminAuditLogItemResponse(1L, AuditTargetType.POST, 7L,
                        AuditAction.POST_TAKEDOWN, 99L, "ABUSE: 人身攻击",
                        Instant.parse("2026-10-08T00:00:00Z"))),
                "1767225600000000:1"));

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("POST_TAKEDOWN"))
                .andExpect(jsonPath("$.data.items[0].operatorId").value(99))
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].studentId").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("1767225600000000:1"));
    }

    @Test
    @DisplayName("按动作与时间窗筛选：原样透传给服务层")
    void shouldPassFilters() throws Exception {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-08T00:00:00Z");
        when(auditLogQueryService.page(eq(AuditAction.ROLE_GRANT), eq(from), eq(to), eq("1:2")))
                .thenReturn(CursorPage.empty());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("action", "ROLE_GRANT")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("cursor", "1:2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(auditLogQueryService).page(AuditAction.ROLE_GRANT, from, to, "1:2");
    }

    @Test
    @DisplayName("动作枚举非法返回 400 而不是 500")
    void shouldRejectUnknownAction() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").param("action", "NOT_AN_ACTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }
}
