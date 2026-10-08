package com.qinglan.user.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.user.dto.AdminMeResponse;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端「当前身份」接口：角色 + 权限点集合，供前端渲染菜单与按钮。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMeController {

    private final UserService userService;

    /**
     * 当前管理端身份（P5 设计 7.2）。
     *
     * <p>只要求「已认证」而不要求任何权限点：它要回答的正是「我有哪些权限点」，若自身再要求权限点，
     * 权限点为空的新角色连自己该看到什么都不知道。这也不破坏「管理员接口默认拒绝」——
     * 它只返回调用者**自己的**角色与权限，不含任何他人数据。</p>
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AdminMeResponse> me() {
        return ApiResponse.ok(userService.currentAdminIdentity(CurrentUser.requireUserId()));
    }
}
