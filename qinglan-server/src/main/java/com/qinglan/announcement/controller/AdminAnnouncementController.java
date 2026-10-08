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
 * 管理端公告接口。权限点全部写在方法级（P5 设计 7.1）：
 * 建草稿与查看归 {@code ANNOUNCEMENT_DRAFT}（管理员可用，保持 P3 以来的现状），
 * 发布 / 撤回是仅超管的 {@code ANNOUNCEMENT_MANAGE}。
 *
 * <p>所有动作通过 Service 落审计日志（不变量 #7）。</p>
 */
@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
@Validated
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_DRAFT')")
    public ApiResponse<AdminAnnouncementItemResponse> create(@Valid @RequestBody CreateAnnouncementRequest request) {
        Long id = announcementService.createDraft(CurrentUser.requireUserId(), request);
        return ApiResponse.ok(announcementService.adminItem(id));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        announcementService.publish(CurrentUser.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        announcementService.revoke(CurrentUser.requireUserId(), id);
        return ApiResponse.ok();
    }

    /** 公告列表：全状态、游标分页 */
    @GetMapping
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_DRAFT')")
    public ApiResponse<CursorPage<AdminAnnouncementItemResponse>> list(
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(announcementService.adminList(cursor));
    }
}
