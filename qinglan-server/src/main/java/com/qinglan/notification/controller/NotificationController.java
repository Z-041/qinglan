package com.qinglan.notification.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.notification.dto.NotificationItemResponse;
import com.qinglan.notification.dto.UnreadCountResponse;
import com.qinglan.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息中心接口。全部面向当前登录用户，无方法级 {@code @PreAuthorize}——
 * {@code SecurityConfig} 的 {@code anyRequest().authenticated()} 已兜住，
 * 且取用户一律走 {@link CurrentUser#requireUserId()}，未登录即 401。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<CursorPage<NotificationItemResponse>> list(@RequestParam(required = false) String cursor) {
        return ApiResponse.ok(notificationService.list(CurrentUser.requireUserId(), cursor));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(new UnreadCountResponse(notificationService.unreadCount(CurrentUser.requireUserId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(CurrentUser.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(CurrentUser.requireUserId());
        return ApiResponse.ok();
    }
}
