package com.qinglan.announcement.dto;

import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.announcement.entity.AnnouncementStatus;

import java.time.Instant;

/**
 * 管理端公告列表项 / 建草稿返回。含全部状态，供管理端列表与发布前确认。
 */
public record AdminAnnouncementItemResponse(
        Long id,
        String title,
        AnnouncementLevel level,
        AnnouncementStatus status,
        Instant startAt,
        Instant endAt,
        int minReadSeconds,
        boolean requireScrollToEnd,
        boolean requireConfirm,
        Instant publishedAt,
        Instant revokedAt,
        Instant createdAt
) {
}
