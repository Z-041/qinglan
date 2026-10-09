package com.qinglan.user.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.service.UserService;
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
 * 学生端「我的档案」接口（Web 层切片）。只要求已认证，未登录由 {@code CurrentUser} 抛 401。
 */
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final long USER_ID = 7L;

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

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(USER_ID, null, List.of()));
    }

    @Test
    @DisplayName("未登录访问个人档案返回 401")
    void shouldRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("已登录返回本人档案，用户取自登录态")
    void shouldReturnOwnProfile() throws Exception {
        authenticate();
        User user = User.register("emailHash", "studentHash", "enc", "salt");
        when(userService.requireById(USER_ID)).thenReturn(user);
        when(userService.toProfile(user)).thenReturn(new UserProfileResponse(
                USER_ID, "STUDENT", UserStatus.ACTIVE, 100, null,
                Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.id").value(USER_ID))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    @DisplayName("用户不存在返回 404")
    void shouldReturn404WhenUserMissing() throws Exception {
        authenticate();
        when(userService.requireById(USER_ID)).thenThrow(new BizException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-001"));
    }
}
