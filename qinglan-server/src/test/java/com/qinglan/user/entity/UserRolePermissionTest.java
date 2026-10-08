package com.qinglan.user.entity;

import com.qinglan.common.security.AdminPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限矩阵的守门测试（P5 设计 4.2）。
 *
 * <p>这张矩阵是安全资产：端点注解、令牌 authorities、前端菜单三处都从它派生，改错一处就是越权或
 * 功能不可达。因此把矩阵逐项写死在测试里——调整矩阵必须同时改代码与测试，逼着改动者显式确认。</p>
 */
class UserRolePermissionTest {

    private static final Set<AdminPermission> REVIEWER_EXPECTED = EnumSet.of(
            AdminPermission.MODERATION_VIEW,
            AdminPermission.POST_REVIEW,
            AdminPermission.REPORT_DISMISS,
            AdminPermission.PROPOSAL_CREATE,
            AdminPermission.PROPOSAL_VOTE,
            AdminPermission.STATS_VIEW);

    private static final Set<AdminPermission> ADMIN_EXPECTED = EnumSet.of(
            AdminPermission.MODERATION_VIEW,
            AdminPermission.POST_REVIEW,
            AdminPermission.REPORT_DISMISS,
            AdminPermission.PROPOSAL_CREATE,
            AdminPermission.PROPOSAL_VOTE,
            AdminPermission.STATS_VIEW,
            AdminPermission.APPEAL_REVIEW,
            AdminPermission.USER_MANAGE,
            AdminPermission.ANNOUNCEMENT_DRAFT);

    @Test
    @DisplayName("学生没有任何管理端权限")
    void studentHasNoAdminPermission() {
        assertThat(UserRole.STUDENT.permissions()).isEmpty();
        assertThat(UserRole.STUDENT.isCommittee()).isFalse();
    }

    @Test
    @DisplayName("评议员：治理相关权限齐备，但不管人、不复审、不发公告、不看审计")
    void reviewerMatchesMatrix() {
        assertThat(UserRole.REVIEWER.permissions()).isEqualTo(REVIEWER_EXPECTED);
        assertThat(UserRole.REVIEWER.isCommittee()).isTrue();
    }

    @Test
    @DisplayName("管理员：评议员权限 + 申诉复审 + 用户管理 + 建草稿，但不含发布公告与下放/裁决/撤销/审计")
    void adminMatchesMatrix() {
        assertThat(UserRole.ADMIN.permissions()).isEqualTo(ADMIN_EXPECTED);
        assertThat(UserRole.ADMIN.isCommittee()).isTrue();
        assertThat(UserRole.ADMIN.permissions()).doesNotContain(
                AdminPermission.ANNOUNCEMENT_MANAGE,
                AdminPermission.ROLE_GRANT,
                AdminPermission.PROPOSAL_ARBITRATE,
                AdminPermission.DISPOSITION_REVOKE,
                AdminPermission.AUDIT_READ);
    }

    @Test
    @DisplayName("超管取全集：新增权限点时不会漏给超管")
    void superAdminHasEveryPermission() {
        assertThat(UserRole.SUPER_ADMIN.permissions())
                .containsExactlyInAnyOrder(AdminPermission.values());
        assertThat(UserRole.SUPER_ADMIN.isCommittee()).isFalse();
    }

    @Test
    @DisplayName("三档是包含关系而非并列：评议员 ⊂ 管理员 ⊂ 超管")
    void rolesAreNested() {
        assertThat(UserRole.ADMIN.permissions())
                .containsAll(UserRole.REVIEWER.permissions());
        assertThat(UserRole.SUPER_ADMIN.permissions())
                .containsAll(UserRole.ADMIN.permissions());
    }

    @Test
    @DisplayName("每个权限点都至少被一档非超管角色使用，不留只写给超管的死权限点")
    void everyPermissionIsGrantedToSomeoneBelowSuperAdmin() {
        Set<AdminPermission> granted = EnumSet.noneOf(AdminPermission.class);
        granted.addAll(UserRole.REVIEWER.permissions());
        granted.addAll(UserRole.ADMIN.permissions());

        assertThat(Arrays.stream(AdminPermission.values()).filter(p -> !granted.contains(p)).toList())
                .containsExactlyInAnyOrder(
                        AdminPermission.ANNOUNCEMENT_MANAGE,
                        AdminPermission.ROLE_GRANT,
                        AdminPermission.PROPOSAL_ARBITRATE,
                        AdminPermission.DISPOSITION_REVOKE,
                        AdminPermission.AUDIT_READ);
    }

    @Test
    @DisplayName("委员会上限为 6（表决法定人数的基础）")
    void committeeSizeIsSix() {
        assertThat(UserRole.MAX_COMMITTEE_SIZE).isEqualTo(6);
    }
}
