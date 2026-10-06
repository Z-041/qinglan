package com.qinglan.announcement.controller;

import com.qinglan.announcement.dto.AnnouncementSummaryResponse;
import com.qinglan.announcement.dto.MarkReadRequest;
import com.qinglan.announcement.dto.PendingAnnouncementResponse;
import com.qinglan.announcement.service.AnnouncementService;
import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.security.Viewer;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告学生端接口。登录用户与游客都可访问（游客也要能被强制阅读，见计划 D4/D5）。
 *
 * <p>Controller 只取参、取当前 visitor、装配 DTO；业务判断全在 Service。</p>
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 待读一条；无未读时 data 为 null */
    @GetMapping("/pending")
    public ApiResponse<PendingAnnouncementResponse> pending(@RequestParam(required = false) String deviceId) {
        Viewer viewer = CurrentUser.viewer();
        return ApiResponse.ok(
                announcementService.findPending(viewer.userId(), deviceId).orElse(null));
    }

    /**
     * 标记已读。匿名可写是刻意的（游客也要记已读），只写一条已读记录、有唯一键幂等、不落任何身份明文。
     * 时长与是否勾选由服务端校验。
     */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                      @RequestParam(required = false) String deviceId,
                                      @Valid @RequestBody MarkReadRequest request) {
        announcementService.markRead(id, CurrentUser.currentUserId().orElse(null), deviceId, request);
        return ApiResponse.ok();
    }

    /** 公告历史：游标分页，标「我是否已读」 */
    @GetMapping
    public ApiResponse<CursorPage<AnnouncementSummaryResponse>> history(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String deviceId) {
        Viewer viewer = CurrentUser.viewer();
        return ApiResponse.ok(announcementService.history(cursor, viewer.userId(), deviceId));
    }
}
