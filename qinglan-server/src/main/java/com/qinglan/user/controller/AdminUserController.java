package com.qinglan.user.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.user.dto.AdminUserQuery;
import com.qinglan.user.dto.BanUserRequest;
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

/**
 * 用户治理接口。所有操作均通过 UserService 落审计日志（不变量 #7）。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminUserController {

    private final UserService userService;

    /** 用户列表：键集分页 + 状态 / 角色 / 信用分 / 注册时间筛选（不提供身份维度检索） */
    @GetMapping
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
    public ApiResponse<Void> ban(@PathVariable Long userId, @Valid @RequestBody BanUserRequest request) {
        userService.ban(userId, Duration.ofDays(request.days()), request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{userId}/unban")
    public ApiResponse<Void> unban(@PathVariable Long userId, @Valid @RequestBody ReasonRequest request) {
        userService.unban(userId, request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{userId}/credit")
    public ApiResponse<Void> adjustCredit(@PathVariable Long userId, @Valid @RequestBody CreditAdjustRequest request) {
        userService.adjustCredit(userId, request.delta(), request.reason(), CurrentUser.requireUserId());
        return ApiResponse.ok();
    }
}
