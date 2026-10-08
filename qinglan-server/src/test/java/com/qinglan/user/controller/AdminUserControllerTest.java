package com.qinglan.user.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.user.dto.AdminUserQuery;
import com.qinglan.user.dto.AssignableRole;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端用户治理接口的参数校验与状态码映射（Web 层切片）。
 *
 * <p>权限判定（学生访问 403、管理员可操作）由 {@code SecuritySkeletonTest} 在完整上下文中验证，
 * 此处关闭过滤器只校验 DTO 约束与状态码。</p>
 */
@WebMvcTest(controllers = AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

    private static final long OPERATOR_ID = 99L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 认证态按真实过滤器写入的权威集合构造：ROLE_* + 该角色的权限点（P5 权限模型） */
    private void authenticateAsAdmin() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        UserRole.ADMIN.permissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.name())));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(OPERATOR_ID, null, authorities));
    }

    @Test
    @DisplayName("封禁天数为 0 返回 400")
    void shouldRejectZeroBanDays() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":0,\"reason\":\"测试\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("封禁天数超出上限返回 400")
    void shouldRejectExcessiveBanDays() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":3651,\"reason\":\"测试\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("封禁理由为空返回 400")
    void shouldRejectBlankBanReason() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("信用分调整超出单次上限返回 400")
    void shouldRejectExcessiveCreditDelta() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":101,\"reason\":\"测试\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("合法封禁返回成功，且操作人取自当前登录用户")
    void shouldBanSuccessfully() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users/1/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7,\"reason\":\"人身攻击\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(userService).ban(1L, Duration.ofDays(7), "人身攻击", OPERATOR_ID);
    }

    @Test
    @DisplayName("合法信用分调整返回成功")
    void shouldAdjustCreditSuccessfully() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users/1/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-20,\"reason\":\"违规扣分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(userService).adjustCredit(1L, -20, "违规扣分", OPERATOR_ID);
    }

    @Test
    @DisplayName("用户列表返回游标页")
    void shouldListUsers() throws Exception {
        when(userService.listUsers(any())).thenReturn(CursorPage.of(
                List.of(new UserProfileResponse(7L, "STUDENT", UserStatus.ACTIVE, 100, null,
                        Instant.parse("2026-01-01T00:00:00Z"))),
                "1767225600000:7"));

        mockMvc.perform(get("/api/admin/users").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.nextCursor").value("1767225600000:7"));
    }

    @Test
    @DisplayName("信用分下界为负返回 400")
    void shouldRejectNegativeMinCredit() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("minCredit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.code()));
    }

    @Test
    @DisplayName("筛选参数类型错误返回 400 而不是 500")
    void shouldRejectMalformedStatusFilter() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.code()));
    }

    // ------------------------------------------------------------------ 权限下放（P5）

    @Test
    @DisplayName("下放管理员：合法请求透传到服务层，操作人取自登录态")
    void shouldChangeRole() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users/7/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"补一名管理员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(userService).changeRole(7L, AssignableRole.ADMIN, "补一名管理员", OPERATOR_ID);
    }

    @Test
    @DisplayName("请求体里写 SUPER_ADMIN 在反序列化阶段就被拒（不进入服务层）")
    void shouldRejectSuperAdminAsAssignableRole() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/users/7/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPER_ADMIN\",\"reason\":\"想当超管\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changeRole(any(), any(), any(), any());
    }

    @Test
    @DisplayName("缺少理由返回 400：权限变更必须留下「为什么」")
    void shouldRejectRoleChangeWithoutReason() throws Exception {
        mockMvc.perform(post("/api/admin/users/7/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.code()));
    }

    @Test
    @DisplayName("委员会名单返回全部成员（管理员 + 评议员）")
    void shouldListCommittee() throws Exception {
        when(userService.listCommittee()).thenReturn(List.of(
                new UserProfileResponse(1L, "SUPER_ADMIN", UserStatus.ACTIVE, 100, null,
                        Instant.parse("2026-01-01T00:00:00Z")),
                new UserProfileResponse(2L, "REVIEWER", UserStatus.ACTIVE, 100, null,
                        Instant.parse("2026-01-02T00:00:00Z"))));

        mockMvc.perform(get("/api/admin/users/committee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data[1].role").value("REVIEWER"));
    }
}
