package com.qinglan.notification.entity;

import com.qinglan.common.notify.NotificationType;
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
 * 个人通知。
 *
 * <p>内容是**快照**：{@code title} / {@code body} / {@code link} 落库后不随源对象变化
 * （计划 D14）。因此**不得**写入作者身份字段——{@code body} 只放评论文本等面向接收者的内容，
 * 不含 {@code authorId}、匿名代号、邮箱等任何可关联字段（不变量 #1 的引申）。</p>
 *
 * <p>不提供 status setter：已读只经 {@link #markRead(Instant)} 迁移，幂等且不覆盖首次已读时刻。</p>
 */
@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @Column(name = "link", length = 200)
    private String link;

    /** 已读时刻；NULL 表示未读 */
    @Column(name = "read_at")
    private Instant readAt;

    /** 新建通知：落库即未读（{@code readAt} 留空），已读只能由 {@link #markRead(Instant)} 推进。 */
    public static Notification of(Long userId, NotificationType type, String title, String body, String link) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.link = link;
        notification.readAt = null;
        return notification;
    }

    /**
     * 标记已读，**幂等**：已读的不再改写 {@code readAt}，避免首次已读时刻被重复标记冲掉。
     */
    public void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
