package com.qinglan.notification.dto;

import com.qinglan.common.notify.NotificationType;

import java.time.Instant;

/**
 * 消息中心列表项。
 *
 * <p>类型复用 {@code common.notify.NotificationType}，不在 notification 模块另造枚举——
 * 否则触发方与消费方各持一套类型，映射迟早漂移。</p>
 */
public record NotificationItemResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        String link,
        Instant readAt,
        Instant createdAt
) {
}
