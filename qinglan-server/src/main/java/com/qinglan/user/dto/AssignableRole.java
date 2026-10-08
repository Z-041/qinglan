package com.qinglan.user.dto;

import com.qinglan.user.entity.UserRole;

/**
 * 可下放的角色（P5 设计 7.3）。
 *
 * <p>刻意与 {@link UserRole} 分开、且**不含 {@code SUPER_ADMIN}**：超管唯一且是初始账号，
 * 任何接口都不该能授予它。用独立枚举而不是复用 UserRole，是为了让「请求体里写 SUPER_ADMIN」
 * 在**反序列化阶段**就 400——少一条需要靠服务层判断去堵的路，也就少一个可能被绕过的点。</p>
 */
public enum AssignableRole {

    /** 管理员 */
    ADMIN,
    /** 学生评议员 */
    REVIEWER,
    /** 学生（即收回管理权限） */
    STUDENT;

    /** 是否属于「委员会」（参与表决）。上限与分母快照都只约束这一档 */
    public boolean isCommittee() {
        return this == ADMIN || this == REVIEWER;
    }

    /**
     * 映射回持久化角色。用 {@code valueOf} 依赖两边名字一致，
     * 由 {@code AssignableRoleTest} 强制这一前提（改名必须一起改）。
     */
    public UserRole toUserRole() {
        return UserRole.valueOf(name());
    }
}
