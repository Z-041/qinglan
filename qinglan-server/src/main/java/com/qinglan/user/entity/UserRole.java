package com.qinglan.user.entity;

import com.qinglan.common.security.AdminPermission;

import java.util.Set;

/**
 * 用户角色。GUEST（游客）不落库，仅作为未认证请求的语义角色。
 *
 * <p><b>角色 → 权限点的映射只此一处</b>（P5 设计 4.2 的矩阵）：接口注解、令牌 authorities、
 * 前端菜单三处都从这里派生。新增角色或调整矩阵时只改本文件 + 补 {@code UserRolePermissionTest}，
 * 不要在任何端点里写 {@code hasRole('ADMIN')} 这种判断。</p>
 *
 * <p>层级关系是「包含」而非「并列」：REVIEWER ⊂ ADMIN ⊂ SUPER_ADMIN（各集合见下方常量），
 * 一条测试会强制这个关系，避免出现「管理员有、评议员没有、超管又没有」这类错配。</p>
 */
public enum UserRole {

    /** 普通学生：能进学生端，无任何管理端权限 */
    STUDENT,

    /** 学生评议员：参与内容治理（核准、处置提案与投票），不管人、不发公告、不看审计 */
    REVIEWER,

    /** 管理员：评议员的全部权限 + 申诉复审 + 用户管理 + 建公告草稿（发布仍归超管） */
    ADMIN,

    /** 超级管理员：全部权限。系统内唯一，且是初始账号（P5 D4） */
    SUPER_ADMIN;

    /** 委员会人数上限（管理员 + 评议员）。它是表决法定人数的基础，见 P5 D3 */
    public static final int MAX_COMMITTEE_SIZE = 6;

    private static final Set<AdminPermission> COMMITTEE_PERMISSIONS = Set.of(
            AdminPermission.MODERATION_VIEW,
            AdminPermission.POST_REVIEW,
            AdminPermission.REPORT_DISMISS,
            AdminPermission.PROPOSAL_CREATE,
            AdminPermission.PROPOSAL_VOTE,
            AdminPermission.STATS_VIEW);

    private static final Set<AdminPermission> ADMIN_PERMISSIONS = Set.of(
            AdminPermission.MODERATION_VIEW,
            AdminPermission.POST_REVIEW,
            AdminPermission.REPORT_DISMISS,
            AdminPermission.PROPOSAL_CREATE,
            AdminPermission.PROPOSAL_VOTE,
            AdminPermission.STATS_VIEW,
            // 管理员独有：复审要判断「前一次处置是否合理」，由更高一档角色承担；用户管理是「管人」
            AdminPermission.APPEAL_REVIEW,
            AdminPermission.USER_MANAGE,
            // 建草稿保持现状（普通管理员一直可以建草稿，只有发布/撤回是超管）
            AdminPermission.ANNOUNCEMENT_DRAFT);

    /** 该角色在管理端拥有的权限点；超管取全集，避免新增权限点时忘了给超管 */
    public Set<AdminPermission> permissions() {
        return switch (this) {
            case STUDENT -> Set.of();
            case REVIEWER -> COMMITTEE_PERMISSIONS;
            case ADMIN -> ADMIN_PERMISSIONS;
            case SUPER_ADMIN -> Set.of(AdminPermission.values());
        };
    }

    /**
     * 是否属于「委员会」（参与表决的成员）。上限 {@link #MAX_COMMITTEE_SIZE} 只约束这一档，
     * 它决定表决的分母快照（P5 D3 / D6）。
     */
    public boolean isCommittee() {
        return this == REVIEWER || this == ADMIN;
    }
}
