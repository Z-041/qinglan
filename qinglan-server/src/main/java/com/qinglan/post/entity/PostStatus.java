package com.qinglan.post.entity;

/**
 * 主帖审核状态的持久化名。
 *
 * <p>状态迁移的合法性由 {@link ReviewState} 裁决（不变量 #5），本枚举只负责落库取值，
 * 不得绕过状态机直接赋值。</p>
 */
public enum PostStatus {
    DRAFT,
    PENDING,
    REVIEWING,
    APPROVED,
    REJECTED,
    TAKEN_DOWN
}
