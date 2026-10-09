package com.qinglan.post.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 图片上传配置。
 *
 * @param maxPerWindow        单个用户在滑动窗口内的上传次数上限，超出拒绝
 * @param window              限流窗口长度
 * @param orphanCleanupEnabled 是否开启孤儿回收定时任务
 * @param orphanRetention     上传后超过该时长仍未绑定到任何帖子的对象将被回收（对象 + 登记行）
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.upload")
public record UploadProperties(
        @Min(1) int maxPerWindow,
        @NotNull Duration window,
        // 给默认值而非必填：本机 application.yml 是老配置、没有本项，必填会让本地起不来
        @DefaultValue("true") boolean orphanCleanupEnabled,
        @DefaultValue("24h") Duration orphanRetention
) {
}
