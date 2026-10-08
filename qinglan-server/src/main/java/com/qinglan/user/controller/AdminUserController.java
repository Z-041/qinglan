package com.qinglan.user.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.user.dto.AdminUserQuery;
import com.qinglan.user.dto.BanUserRequest;
import com.qinglan.user.dto.ChangeUserRoleRequest;
import com.qinglan.user.dto.CreditAdjustRequest;
import com.qinglan.user.dto.ReasonRequest;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 用户治理接口。所有操作均通过 UserService 落审计日志（不变量 #7）。
 *
 * <p>权限点写在**方法级**（P5 设计 7.1）：类级放行 + 方法级遗漏是最常见的越权来源，
 * 所以类上不再做任何放宽，每个端点自己声明所需的权限点。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final UserService userService;

    /** 用户列表：键集分页 + 状态 / 角色 / 信用分 / 注册时间筛选（不提供身份维度检索） */
    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<CursorPage<UserProfileResponse>> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minCredit,
            @RequestParam(required = false) @Min(0) @Max(100) Integer maxCredit,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) String cursor) {
        AdminUserQuery query = new AdminUserQuery(
                status, role, minCredit, maxCredit, createdFrom, createdTo, cursor);
        return ApiResponse.ok(userService.listUsers(query));
    }

    @PostMapping("/{userId}/ban")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> ban(@PathVariable Long userId, @Valid @RequestBody BanUserRequest request) {
        userService.ban(userId, Duration.ofDays(request.days()), request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{userId}/unban")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> unban(@PathVariable Long userId, @Valid @RequestBody ReasonRequest request) {
        userService.unban(userId, request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{userId}/credit")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> adjustCredit(@PathVariable Long userId, @Valid @RequestBody CreditAdjustRequest request) {
        userService.adjustCredit(userId, request.delta(), request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    /**
     * 委员会成员名单（管理员 + 评议员），供权限管理页展示「当前 N / 6」。
     *
     * <p>刻意不分页：人数受 {@code UserRole#MAX_COMMITTEE_SIZE}（6）硬约束，是有界集合。</p>
     */
    @GetMapping("/committee")
    @PreAuthorize("hasAuthority('ROLE_GRANT')")
    public ApiResponse<List<UserProfileResponse>> committee() {
        return ApiResponse.ok(userService.listCommittee());
    }

    /**
     * 下放 / 收回 / 互转管理角色。仅超级管理员（{@code ROLE_GRANT}），且只能授予
     * {@code ADMIN} / {@code REVIEWER}——请求体里出现 {@code SUPER_ADMIN} 会在反序列化阶段被拒。
     */
    @PostMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('ROLE_GRANT')")
    public ApiResponse<Void> changeRole(@PathVariable Long userId,
                                        @Valid @RequestBody ChangeUserRoleRequest request) {
        userService.changeRole(userId, request.role(), request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }
}
