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
        User admin = createAdmin("admin@qinglan.edu.cn", "2023010004");
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

    // ---------------------------------------------------------------- P5 权限模型

    @Test
    @DisplayName("评议员可看待审帖子（MODERATION_VIEW），但进不了用户管理（无 USER_MANAGE）")
    void shouldScopeReviewerToModerationOnly() throws Exception {
        User reviewer = createUser("reviewer@qinglan.edu.cn", "2023010010", UserRole.REVIEWER);
        String token = jwtService.issueAccessToken(reviewer.getId(), UserRole.REVIEWER);

        mockMvc.perform(get("/api/admin/pending-posts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));
    }

    @Test
    @DisplayName("审计日志只对超管开放：管理员读它是 403（AUDIT_READ 不在管理员的权限集里）")
    void shouldRestrictAuditLogToSuperAdmin() throws Exception {
        User admin = createAdmin("auditor@qinglan.edu.cn", "2023010011");
        User superAdmin = createUser("root@qinglan.edu.cn", "2023010012", UserRole.SUPER_ADMIN);
        String adminToken = jwtService.issueAccessToken(admin.getId(), UserRole.ADMIN);
        String superAdminToken = jwtService.issueAccessToken(superAdmin.getId(), UserRole.SUPER_ADMIN);

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("权限被收回后旧令牌立即失效：写请求 403，不必等令牌到期（P5 D9）")
    void shouldRejectStaleTokenAfterRoleRevoked() throws Exception {
        User admin = createAdmin("was-admin@qinglan.edu.cn", "2023010013");
        User target = createStudent("target@qinglan.edu.cn", "2023010014");
        String token = jwtService.issueAccessToken(admin.getId(), UserRole.ADMIN);

        // 令牌仍然写着 ROLE_ADMIN，但数据库里已被收回成学生
        admin.changeRole(UserRole.STUDENT);

        mockMvc.perform(post("/api/admin/users/{userId}/ban", target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(banBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));
    }

    @Test
    @DisplayName("超级管理员不可被封禁（唯一超管被锁死就没人能解锁）")
    void shouldProtectSuperAdminFromBan() throws Exception {
        User superAdmin = createUser("root2@qinglan.edu.cn", "2023010015", UserRole.SUPER_ADMIN);
        User anotherSuperAdmin = createUser("root3@qinglan.edu.cn", "2023010016", UserRole.SUPER_ADMIN);
        String token = jwtService.issueAccessToken(superAdmin.getId(), UserRole.SUPER_ADMIN);

        mockMvc.perform(post("/api/admin/users/{userId}/ban", anotherSuperAdmin.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(banBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-006"));
    }

    @Test
    @DisplayName("下放权限：超管把学生提为评议员，角色落库并写 ROLE_GRANT 审计")
    void shouldGrantRoleThroughEndpoint() throws Exception {
        User superAdmin = createUser("root4@qinglan.edu.cn", "2023010017", UserRole.SUPER_ADMIN);
        User candidate = createStudent("candidate@qinglan.edu.cn", "2023010018");
        String token = jwtService.issueAccessToken(superAdmin.getId(), UserRole.SUPER_ADMIN);

        mockMvc.perform(post("/api/admin/users/{userId}/role", candidate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\",\"reason\":\"学生自助申请，已审批\"}"))
                .andExpect(status().isOk());

        assertThat(candidate.getRole()).isEqualTo(UserRole.REVIEWER);
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_log WHERE target_type = 'USER' AND target_id = ? "
                        + "AND action = 'ROLE_GRANT' AND operator_id = ?",
                Integer.class, candidate.getId(), superAdmin.getId());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    @DisplayName("提案接口按权限点放行：评议员可看提案列表（PROPOSAL_VOTE），学生被拒（P5 二期）")
    void shouldScopeProposalsByPermission() throws Exception {
        User reviewer = createUser("proposer@qinglan.edu.cn", "2023010019", UserRole.REVIEWER);
        User student = createStudent("plain@qinglan.edu.cn", "2023010020");
        String reviewerToken = jwtService.issueAccessToken(reviewer.getId(), UserRole.REVIEWER);
        String studentToken = jwtService.issueAccessToken(student.getId(), UserRole.STUDENT);

        mockMvc.perform(get("/api/admin/proposals").header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/admin/proposals").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));
    }

    @Test
    @DisplayName("僵局裁决只对超管开放：管理员读待裁决列表与裁决都是 403（PROPOSAL_ARBITRATE 不在其权限集）")
    void shouldRestrictArbitrationToSuperAdmin() throws Exception {
        User admin = createAdmin("arbiter@qinglan.edu.cn", "2023010021");
        User superAdmin = createUser("root5@qinglan.edu.cn", "2023010022", UserRole.SUPER_ADMIN);
        String adminToken = jwtService.issueAccessToken(admin.getId(), UserRole.ADMIN);
        String superAdminToken = jwtService.issueAccessToken(superAdmin.getId(), UserRole.SUPER_ADMIN);

        mockMvc.perform(get("/api/admin/proposals/escalated").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));

        mockMvc.perform(post("/api/admin/proposals/{id}/arbitrate", 1L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"GRANT\",\"reason\":\"放行\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));

        // 超管可读待裁决列表（内容为空也应是 200）
        mockMvc.perform(get("/api/admin/proposals/escalated")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("撤销处置只对超管开放：管理员不能恢复可见或撤销隐藏（若谁都能撤，多数决就不构成约束）")
    void shouldRestrictDispositionRevokeToSuperAdmin() throws Exception {
        User admin = createAdmin("revoker@qinglan.edu.cn", "2023010031");
        User superAdmin = createUser("root6@qinglan.edu.cn", "2023010032", UserRole.SUPER_ADMIN);
        String adminToken = jwtService.issueAccessToken(admin.getId(), UserRole.ADMIN);
        String superAdminToken = jwtService.issueAccessToken(superAdmin.getId(), UserRole.SUPER_ADMIN);

        // 管理员：两个撤销入口都被权限点挡住
        mockMvc.perform(post("/api/admin/posts/{id}/restore", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"我觉得不该下架\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));

        mockMvc.perform(post("/api/admin/posts/{id}/show-contact", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"撤销隐藏\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));

        // 超管：过了权限点这一关，因帖子不存在而 404（证明拦的是权限而不是路径）
        mockMvc.perform(post("/api/admin/posts/{id}/restore", 999999L)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"确系误判\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-002"));
    }

    // ---------------------------------------------------------------- P6 新增能力

    @Test
    @DisplayName("导出端点按权限点放行：审计导出仅超管、用户导出要 USER_MANAGE，且返回 CSV")
    void shouldScopeExportEndpoints() throws Exception {
        User admin = createAdmin("exp-admin@qinglan.edu.cn", "2023010051");
        User superAdmin = createUser("exp-root@qinglan.edu.cn", "2023010052", UserRole.SUPER_ADMIN);
        User student = createStudent("exp-student@qinglan.edu.cn", "2023010053");
        String adminToken = jwtService.issueAccessToken(admin.getId(), UserRole.ADMIN);
        String superAdminToken = jwtService.issueAccessToken(superAdmin.getId(), UserRole.SUPER_ADMIN);
        String studentToken = jwtService.issueAccessToken(student.getId(), UserRole.STUDENT);

        // 审计导出：管理员无 AUDIT_READ → 403；超管 → 200 且是 CSV
        mockMvc.perform(get("/api/admin/audit-logs/export").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));
        mockMvc.perform(get("/api/admin/audit-logs/export").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")));

        // 用户导出：学生无 USER_MANAGE → 403；管理员 → 200 且是 CSV
        mockMvc.perform(get("/api/admin/users/export").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-001"));
        mockMvc.perform(get("/api/admin/users/export").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")));
    }

    @Test
    @DisplayName("待办计数与上下文历史按 MODERATION_VIEW 放行：学生被拒、管理员可读")
    void shouldScopeTodoCountsAndHistory() throws Exception {
        User admin = createAdmin("todo-admin@qinglan.edu.cn", "2023010054");
        User student = createStudent("todo-student@qinglan.edu.cn", "2023010055");
        String adminToken = jwtService.issueAccessToken(admin.getId(), UserRole.ADMIN);
        String studentToken = jwtService.issueAccessToken(student.getId(), UserRole.STUDENT);

        mockMvc.perform(get("/api/admin/todo-counts").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/todo-counts").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPosts").exists())
                .andExpect(jsonPath("$.data.pendingReports").exists());

        mockMvc.perform(get("/api/admin/review-context/history")
                        .param("targetType", "POST").param("targetId", "999999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("我的帖子需登录：游客 401，本人 200")
    void shouldRequireAuthForMyPosts() throws Exception {
        User student = createStudent("mine@qinglan.edu.cn", "2023010056");
        String token = jwtService.issueAccessToken(student.getId(), UserRole.STUDENT);

        mockMvc.perform(get("/api/posts/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));

        mockMvc.perform(get("/api/posts/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("信息流 sort=hot：游客可用，返回游标分页且不含作者身份")
    void shouldServeHotFeed() throws Exception {
        mockMvc.perform(get("/api/posts").param("sort", "hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    private String banBody() throws Exception {
        return objectMapper.writeValueAsString(new BanRequestBody(7, "人身攻击"));
    }

    private record BanRequestBody(int days, String reason) {
    }

    private User createStudent(String email, String studentId) {
        return userService.createVerifiedStudent(cryptoService.hashEmail(email), studentId);
    }

    /**
     * 建一个**数据库里也是该角色**的用户。
     *
     * <p>P5 之后这是必需的：管理端写入口会比对「令牌角色 = 数据库角色」，若只发一个 ADMIN 令牌
     * 而库里还是 STUDENT，请求会被当场拒掉（那正是 D9 要的效果）。</p>
     */
    private User createUser(String email, String studentId, UserRole role) {
        User user = createStudent(email, studentId);
        user.changeRole(role);
        return user;
    }

    private User createAdmin(String email, String studentId) {
        return createUser(email, studentId, UserRole.ADMIN);
    }
}
