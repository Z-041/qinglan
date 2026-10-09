package com.qinglan.post.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 发帖相关配置。
 *
 * @param minCreditToPost  允许发帖的最低信用分，低于该值拒绝发布（设计方案第二章）
 * @param maxImagesPerPost 单帖图片数量上限（设计方案第三章：≤9 图）
 * @param hotWindow        热门排序的候选时间窗口（近 N 天内的帖子才参与算分），默认 7 天
 * @param hotMaxCandidates 热门排序单次参与算分的候选上限，默认 500——把「窗口 + 条数」双重界定，
 *                         避免全表算分（安全红线禁止无限制扫描）；超出部分不参与排名
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.post")
public record PostProperties(
        @Min(0) @Max(100) int minCreditToPost,
        @Min(1) @Max(9) int maxImagesPerPost,
        @DefaultValue("7d") Duration hotWindow,
        @DefaultValue("500") @Min(1) @Max(5000) int hotMaxCandidates
) {
}
