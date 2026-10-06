package com.qinglan.announcement.entity;

/**
 * 公告级别。
 *
 * <p><b>声明顺序即优先级顺序</b>，{@code URGENT} 最高。待读查询按此优先级取一条，
 * 由 {@code AnnouncementRepository#findPending} 的显式排序保证；新增级别须同步该排序。</p>
 */
public enum AnnouncementLevel {
    URGENT,
    NORMAL,
    ACTIVITY
}
