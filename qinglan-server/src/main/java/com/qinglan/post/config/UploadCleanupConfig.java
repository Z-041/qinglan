package com.qinglan.post.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时调度。放在 post 模块内（而非全局配置类），让「谁需要调度」与模块边界一致。
 *
 * <p>当前唯一的定时任务是 {@code UploadOrphanCleanupService}（孤儿上传回收）。</p>
 */
@Configuration
@EnableScheduling
public class UploadCleanupConfig {
}
