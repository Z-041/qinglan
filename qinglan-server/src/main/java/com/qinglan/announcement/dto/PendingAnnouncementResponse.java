package com.qinglan.announcement.dto;

import com.qinglan.announcement.entity.AnnouncementLevel;

import java.time.Instant;

/**
 * 待读公告（供公开接口与发帖 gate 共用）。428 响应体即本类型，前端据此直接弹窗。
 */
public record PendingAnnouncementResponse(
        Long id,
        String title,
        String content,
        AnnouncementLevel level,
        int minReadSeconds,
        boolean requireScrollToEnd,
        boolean requireConfirm,
        Instant publishedAt
) {
}
