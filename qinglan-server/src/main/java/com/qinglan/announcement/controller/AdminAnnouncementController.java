package com.qinglan.announcement.controller;

import com.qinglan.announcement.dto.AdminAnnouncementItemResponse;
import com.qinglan.announcement.dto.CreateAnnouncementRequest;
import com.qinglan.announcement.service.AnnouncementService;
import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import jakarta.validation.Valid;
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

/**
 * 管理端公告接口。类级 {@code ADMIN / SUPER_ADMIN} 且默认拒绝（计划 D8）：
 * 建草稿普通管理员可做，发布 / 撤回收窄到仅 {@code SUPER_ADMIN}。
 *
 * <p>所有动作通过 Service 落审计日志（不变量 #7）。</p>
 */
@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping
    public ApiResponse<AdminAnnouncementItemResponse> create(@Valid @RequestBody CreateAnnouncementRequest request) {
        Long id = announcementService.createDraft(CurrentUser.requireUserId(), request);
        return ApiResponse.ok(announcementService.adminItem(id));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        announcementService.publish(CurrentUser.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        announcementService.revoke(CurrentUser.requireUserId(), id);
        return ApiResponse.ok();
    }

    /** 公告列表：全状态、游标分页 */
    @GetMapping
    public ApiResponse<CursorPage<AdminAnnouncementItemResponse>> list(
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(announcementService.adminList(cursor));
    }
}
