package com.qinglan.user.entity;

/**
 * 账号状态。
 */
public enum UserStatus {
    /** 正常 */
    ACTIVE,
    /** 限时封禁，banned_until 到期后自动恢复 */
    BANNED,
    /** 严重失信黑名单，需人工解除 */
    BLACKLISTED
}
