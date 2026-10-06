package com.qinglan.review.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 审核体系配置。
 *
 * @param autoTakeDownThreshold 待处理举报数达到该值时，帖子自动下架并转人工复核（设计方案：N=3）
 * @param appealWindow          申诉期限：自下架时刻起算，超期不再受理
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.review")
public record ReviewProperties(
        @Min(1) int autoTakeDownThreshold,
        Duration appealWindow
) {

    public ReviewProperties {
        if (appealWindow == null || appealWindow.isZero() || appealWindow.isNegative()) {
            throw new IllegalArgumentException("申诉期限必须为正数时长");
        }
    }
}
