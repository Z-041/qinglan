package com.qinglan.post.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 发帖相关配置。
 *
 * @param minCreditToPost  允许发帖的最低信用分，低于该值拒绝发布（设计方案第二章）
 * @param maxImagesPerPost 单帖图片数量上限（设计方案第三章：≤9 图）
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.post")
public record PostProperties(
        @Min(0) @Max(100) int minCreditToPost,
        @Min(1) @Max(9) int maxImagesPerPost
) {
}
