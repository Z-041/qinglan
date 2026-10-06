package com.qinglan.user.dto;

import com.qinglan.user.entity.UserRole;
import com.qinglan.user.entity.UserStatus;

import java.time.Instant;

/**
 * 管理端用户列表的筛选条件。
 *
 * <p>{@code cursor} 由上一次响应的 {@code nextCursor} 原样带回；为 {@code null} 表示首页。
 * 游标对客户端不透明，前端不解析。</p>
 */
public record AdminUserQuery(
        UserStatus status,
        UserRole role,
        Integer minCredit,
        Integer maxCredit,
        Instant createdFrom,
        Instant createdTo,
        String cursor
) {
}
