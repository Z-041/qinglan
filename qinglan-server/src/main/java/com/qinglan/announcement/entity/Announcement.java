package com.qinglan.announcement.entity;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 公告。
 *
 * <p>状态只能经 {@link #publish(Instant)} / {@link #revoke(Instant)} 变更，由状态机裁决合法性（计划 D7）。
 * 发布后进入强制阅读；撤回不删已读记录，更正走 {@code supersedesId} 发新公告（设计方案 5.1）。</p>
 *
 * <p>不提供 setter，避免绕过状态机直接写 status。</p>
 */
@Getter
@Entity
@Table(name = "announcement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseEntity {

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    /** 正文为 TEXT 列；columnDefinition 与迁移脚本一致，否则 Hibernate 的 ddl-auto=validate 会报类型不符 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private AnnouncementLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnnouncementStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    /** NULL 表示不过期 */
    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "min_read_seconds", nullable = false)
    private int minReadSeconds;

    @Column(name = "require_scroll_to_end", nullable = false)
    private boolean requireScrollToEnd;

    @Column(name = "require_confirm", nullable = false)
    private boolean requireConfirm;

    /** 更正前一条公告的 id；非空表示本条是对旧公告的更正 */
    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** 新建草稿：状态置 DRAFT、发布时间留空，再由 {@link #publish(Instant)} 走状态机发布。 */
    public static Announcement draft(String title, String content, AnnouncementLevel level, Instant startAt,
                                     Instant endAt, int minReadSeconds, boolean requireScrollToEnd,
                                     boolean requireConfirm, Long supersedesId, Long createdBy) {
        Announcement announcement = new Announcement();
        announcement.title = title;
        announcement.content = content;
        announcement.level = level;
        announcement.status = AnnouncementStatus.DRAFT;
        announcement.startAt = startAt;
        announcement.endAt = endAt;
        announcement.minReadSeconds = minReadSeconds;
        announcement.requireScrollToEnd = requireScrollToEnd;
        announcement.requireConfirm = requireConfirm;
        announcement.supersedesId = supersedesId;
        announcement.createdBy = createdBy;
        announcement.publishedAt = null;
        announcement.revokedAt = null;
        return announcement;
    }

    /** 发布：仅 DRAFT 可发布（计划 D7），置 PUBLISHED 并记录发布时间。 */
    public void publish(Instant now) {
        if (status != AnnouncementStatus.DRAFT) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_STATE_ILLEGAL);
        }
        this.status = AnnouncementStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /** 撤回：仅 PUBLISHED 可撤回，置 REVOKED 并记录撤回时间。 */
    public void revoke(Instant now) {
        if (status != AnnouncementStatus.PUBLISHED) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_STATE_ILLEGAL);
        }
        this.status = AnnouncementStatus.REVOKED;
        this.revokedAt = now;
    }

    /**
     * 在给定时刻是否生效：已发布、已到生效开始、且未过期（{@code endAt} 为空表示不过期）。
     */
    public boolean activeAt(Instant now) {
        if (status != AnnouncementStatus.PUBLISHED) {
            return false;
        }
        if (now.isBefore(startAt)) {
            return false;
        }
        return endAt == null || now.isBefore(endAt);
    }
}
