package com.qinglan.user.entity;

/**
 * 用户角色。GUEST（游客）不落库，仅作为未认证请求的语义角色。
 */
public enum UserRole {
    STUDENT,
    REVIEWER,
    ADMIN,
    SUPER_ADMIN
}
