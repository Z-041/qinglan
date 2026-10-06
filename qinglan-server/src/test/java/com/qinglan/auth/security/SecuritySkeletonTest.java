package com.qinglan.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限骨架端到端验证：401 / 200 / 403 / 管理员放行 + 审计留痕。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecuritySkeletonTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("未携带令牌访问受保护接口返回 401")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("无效令牌不构成认证，同样返回 401")
    void shouldReturn401WithMalformedToken() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("所有响应都写安全头：CSP / X-Frame-Options / nosniff / Referrer-Policy / Permissions-Policy")
    void shouldWriteSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"));
    }

    @Test
    @DisplayName("学生令牌可读取自己的档案，且不返回身份字段")
    void shouldAllowStudentToReadOwnProfile() throws Exception {
        User student = createStudent("me@qinglan.edu.cn", "2023010001");
        String token = jwtService.issueAccessToken(student.getId(), UserRole.STUDENT);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(student.getId()))
                .andExpect(jsonPath("$.data.creditScore").value(100))
                .andExpect(jsonPath("$.data.studentId").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    @DisplayName("学生令牌访问管理员接口返回 403")
    void shouldForbidStudentOnAdminEndpoint() throws Exception {
        User student = createStudent("forbidden@qinglan.edu.cn", "2023010002");
        User target = createStudent("victim@qinglan.edu.cn", "2023010003");
        String token = jwtService.issueAccessToken(student.getId(), UserRole.STUDENT);

        mockMvc.perform(post("/api/admin/users/{userId}/ban", target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(banBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));
    }

    @Test
    @DisplayName("管理员封禁成功，用户状态变更并写入审计日志")
    void shouldAllowAdminToBanAndRecordAuditLog() throws Exception {
        User admin = createStudent("admin@qinglan.edu.cn", "2023010004");
        User target = createStudent("rule-breaker@qinglan.edu.cn", "2023010005");
        String token = jwtService.issueAccessToken(admin.getId(), UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/users/{userId}/ban", target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(banBody()))
                .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_log WHERE target_type = 'USER' AND target_id = ? "
                        + "AND action = 'BAN' AND operator_id = ?",
                Integer.class, target.getId(), admin.getId());
        assertThat(auditCount).isEqualTo(1);
    }

    private String banBody() throws Exception {
        return objectMapper.writeValueAsString(new BanRequestBody(7, "人身攻击"));
    }

    private record BanRequestBody(int days, String reason) {
    }

    private User createStudent(String email, String studentId) {
        return userService.createVerifiedStudent(cryptoService.hashEmail(email), studentId);
    }
}
