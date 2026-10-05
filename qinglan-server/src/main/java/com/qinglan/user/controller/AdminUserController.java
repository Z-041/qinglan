package com.qinglan.user.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.user.dto.BanUserRequest;
import com.qinglan.user.dto.CreditAdjustRequest;
import com.qinglan.user.dto.ReasonRequest;
import com.qinglan.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 用户治理接口。所有操作均通过 UserService 落审计日志（不变量 #7）。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminUserController {

    private final UserService userService;

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
