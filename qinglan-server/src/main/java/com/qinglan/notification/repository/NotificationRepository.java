package com.qinglan.notification.repository;

import com.qinglan.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 首页：本用户的通知按 id 倒序（与 UserService / PostService 的纯数值 id 游标一致）。 */
    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable limit);

    /** 续页：从游标 id 之前继续按 id 倒序翻页。 */
    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable limit);

    /** 未读计数：只数 {@code read_at} 为空的行，走 {@code idx_notification_unread}。 */
    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * 单条定位**必须带 userId**：找不到即视为不存在，避免泄漏「别人的通知存在」。
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * 全部已读：批量 UPDATE，禁止查出全部再逐条 save（N+1 / 大更新）。
     *
     * <p>{@code clearAutomatically} 与既有批量更新同源——批量 UPDATE 绕过持久化上下文，
     * 同事务内随后再读会命中一级缓存拿到旧值。</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
