package com.qinglan.announcement.entity;

/**
 * 公告状态机（计划 D7）：{@code DRAFT → PUBLISHED → REVOKED}。
 * {@code REVOKED} 不可再发布——更正走新建 + {@code supersedesId}，不改写原文。
 */
public enum AnnouncementStatus {
    DRAFT,
    PUBLISHED,
    REVOKED
}
