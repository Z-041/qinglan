package com.qinglan.user.dto;

import com.qinglan.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可下放角色与持久化角色的对齐（P5 设计 7.3）。
 *
 * <p>{@code AssignableRole#toUserRole} 依赖两边名字一致，这条测试把它变成强制前提；
 * 同时钉住「可下放角色里永远没有 SUPER_ADMIN」——超管唯一且是初始账号，任何接口都不该能授予它。</p>
 */
class AssignableRoleTest {

    @Test
    @DisplayName("每个可下放角色都能映射到同名持久化角色")
    void shouldMapToSameNamedUserRole() {
        for (AssignableRole role : AssignableRole.values()) {
            assertThat(role.toUserRole().name()).isEqualTo(role.name());
        }
    }

    @Test
    @DisplayName("可下放角色永不含超级管理员（唯一超管是初始账号）")
    void shouldNeverAllowSuperAdmin() {
        assertThat(Arrays.stream(AssignableRole.values()).map(Enum::name))
                .doesNotContain(UserRole.SUPER_ADMIN.name());
    }

    @Test
    @DisplayName("委员会判定：只有管理员与评议员算委员（上限 6 只约束这一档）")
    void shouldMarkOnlyAdminAndReviewerAsCommittee() {
        assertThat(AssignableRole.ADMIN.isCommittee()).isTrue();
        assertThat(AssignableRole.REVIEWER.isCommittee()).isTrue();
        assertThat(AssignableRole.STUDENT.isCommittee()).isFalse();
    }
}
