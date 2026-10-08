package com.qinglan.user.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.user.dto.AdminMeResponse;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端「当前身份」接口（P5 设计 7.2）。
 *
 * <p>它回答的是「我是谁、我有哪些权限点」，因此**不要求任何权限点**；未认证仍由
 * {@code CurrentUser.requireUserId()} 抛 401，这条也在下面覆盖。</p>
 */
@WebMvcTest(controllers = AdminMeController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminMeControllerTest {

    private static final long USER_ID = 42L;

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

    @Test
    @DisplayName("评议员拿到自己的角色与权限点集合，权限点已排序")
    void shouldReturnRoleAndPermissions() throws Exception {
        authenticate();
        when(userService.currentAdminIdentity(USER_ID)).thenReturn(new AdminMeResponse(
                USER_ID, UserRole.REVIEWER.name(),
                List.of("MODERATION_VIEW", "POST_REVIEW", "PROPOSAL_CREATE", "PROPOSAL_VOTE",
                        "REPORT_DISMISS", "STATS_VIEW")));

        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.role").value("REVIEWER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("MODERATION_VIEW"))
                .andExpect(jsonPath("$.data.permissions.length()").value(6));
    }

    @Test
    @DisplayName("未认证返回 401：本接口只要求已认证，但不允许匿名读自己的身份")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_REVIEWER"))));
    }
}
