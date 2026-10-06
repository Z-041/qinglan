package com.qinglan.announcement.entity;

import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 公告已读记录：强制阅读的证据（不变量 #6）。
 *
 * <p>登录用户与游客分开记录——登录用户填 {@code userId}，游客填 {@code deviceId}，两者都可为空但至少有一个。
 * 表上 {@code uk_announcement_read_user} 与 {@code uk_announcement_read_device} 分别保证两个维度的唯一性；
 * 读取时「{@code userId = 我} 或 {@code deviceId = 我的设备} 任一命中即视为已读」（计划 D5）。</p>
 */
@Getter
@Entity
@Table(name = "announcement_read")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementRead extends BaseEntity {

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    /** 登录用户；游客为 null */
    @Column(name = "user_id")
    private Long userId;

    /** 游客设备标识（前端生成并持久化）；登录用户为 null */
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    /** 真实停留时长（毫秒），强制阅读的证据 */
    @Column(name = "read_duration_ms", nullable = false)
    private long readDurationMs;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    public static AnnouncementRead of(Long announcementId, Long userId, String deviceId, Instant readAt,
                                      long readDurationMs, boolean confirmed) {
        AnnouncementRead read = new AnnouncementRead();
        read.announcementId = announcementId;
        read.userId = userId;
        read.deviceId = deviceId;
        read.readAt = readAt;
        read.readDurationMs = readDurationMs;
        read.confirmed = confirmed;
        return read;
    }

    /** 同一维度重复标记已读时更新证据（时长与 confirmed），不新增记录（唯一键幂等）。 */
    public void updateEvidence(Instant readAt, long readDurationMs, boolean confirmed) {
        this.readAt = readAt;
        this.readDurationMs = readDurationMs;
        this.confirmed = confirmed;
    }
}
