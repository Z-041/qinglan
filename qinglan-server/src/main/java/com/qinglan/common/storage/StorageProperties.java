package com.qinglan.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置。P1 只有本地实现；接入对象存储时新增一种 type 即可，
 * 上层（post 模块）只依赖 {@link StorageService} 接口，不感知实现。
 *
 * @param localRoot 本地存储根目录，仅在 type=local 时生效
 */
@ConfigurationProperties(prefix = "qinglan.storage")
public record StorageProperties(String localRoot) {
}
