package com.qinglan.announcement.dto;

import com.qinglan.announcement.entity.AnnouncementLevel;

import java.time.Instant;

/**
 * 学生端公告历史项。
 *
 * @param read 当前 viewer 是否已读（按 userId 或 deviceId 任一命中，见计划 D5）
 */
public record AnnouncementSummaryResponse(
        Long id,
        String title,
        String content,
        AnnouncementLevel level,
        Instant publishedAt,
        boolean read
) {
}
