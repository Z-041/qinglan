package com.qinglan.announcement.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 公告配置。
 *
 * @param minReadSeconds      强制阅读最少秒数默认值（前端倒计时下限；服务端校验已读时长不得低于此值，不变量 #6）
 * @param maxPublishesPerDay  每日公告发布上限，超限返回 429（风控：公告滥用，计划 D9）
 * @param maxReadsPerWindow   单个设备在一个窗口内允许的标记已读次数上限，超限返回 429。
 *                            标记已读是**匿名开放**的写接口（游客也要能被强制阅读），没有上限就能被反复调用刷表
 * @param readRateWindow      标记已读的限流窗口
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.announcement")
public record AnnouncementProperties(
        @Min(0) int minReadSeconds,
        @Min(1) int maxPublishesPerDay,
        @Min(1) int maxReadsPerWindow,
        @NotNull Duration readRateWindow
) {
}
