package com.qinglan.post.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 图片上传配置。
 *
 * @param maxPerWindow 单个用户在滑动窗口内的上传次数上限，超出拒绝
 * @param window       限流窗口长度
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.upload")
public record UploadProperties(
        @Min(1) int maxPerWindow,
        @NotNull Duration window
) {
}
