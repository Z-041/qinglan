package com.qinglan.announcement.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 公告配置。
 *
 * @param minReadSeconds      强制阅读最少秒数默认值（前端倒计时下限；服务端校验已读时长不得低于此值，不变量 #6）
 * @param maxPublishesPerDay  每日公告发布上限，超限返回 429（风控：公告滥用，计划 D9）
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.announcement")
public record AnnouncementProperties(
        @Min(0) int minReadSeconds,
        @Min(1) int maxPublishesPerDay
) {
}
