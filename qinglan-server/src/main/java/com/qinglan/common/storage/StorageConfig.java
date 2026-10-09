package com.qinglan.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 装配 {@link StorageService}：按 {@code qinglan.storage.type} 选本地磁盘或 S3 兼容对象存储。
 *
 * <p>本类**不再注册静态资源映射**（原 {@code addResourceHandlers("/uploads/**")} 已移除）：
 * 图片改由受控端点 {@code GET /uploads/**} 下发，以便按帖子可见性校验（T-27①）。
 * 这样本地与生产行为一致、可见性规则本地也能测。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    private static final String TYPE_S3 = "s3";
    private static final String DEFAULT_REGION = "us-east-1";

    private final StorageProperties properties;

    public StorageConfig(StorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    public StorageService storageService() {
        if (TYPE_S3.equalsIgnoreCase(properties.type())) {
            StorageProperties.S3 s3 = requireCompleteS3Config();
            // 只打 endpoint 与 bucket；访问密钥一律不进日志（AGENTS.md 第九章）
            log.info("文件存储实现：S3 兼容对象存储 endpoint={} bucket={}", s3.endpoint(), s3.bucket());
            return new S3StorageService(
                    S3ClientFactory.create(s3.endpoint(), regionOf(s3), s3.accessKey(), s3.secretKey()),
                    s3.bucket());
        }
        Path root = Path.of(properties.localRoot() == null || properties.localRoot().isBlank()
                ? "./data/uploads"
                : properties.localRoot());
        log.info("文件存储实现：本地磁盘 root={}", root.toAbsolutePath());
        return new LocalStorageService(root);
    }

    /**
     * type=s3 时四项必填。缺项直接在启动期失败并列出**具体配置键与对应环境变量**，
     * 而不是等到第一次上传才抛一个 S3 的通用异常——启动失败比运行期才炸更容易定位。
     */
    private StorageProperties.S3 requireCompleteS3Config() {
        StorageProperties.S3 s3 = properties.s3();
        List<String> missing = new ArrayList<>();
        if (s3 == null) {
            missing.add("qinglan.storage.s3.endpoint（QINGLAN_S3_ENDPOINT）");
            missing.add("qinglan.storage.s3.bucket（QINGLAN_S3_BUCKET）");
            missing.add("qinglan.storage.s3.access-key（QINGLAN_S3_ACCESS_KEY）");
            missing.add("qinglan.storage.s3.secret-key（QINGLAN_S3_SECRET_KEY）");
        } else {
            if (isBlank(s3.endpoint())) {
                missing.add("qinglan.storage.s3.endpoint（QINGLAN_S3_ENDPOINT）");
            }
            if (isBlank(s3.bucket())) {
                missing.add("qinglan.storage.s3.bucket（QINGLAN_S3_BUCKET）");
            }
            if (isBlank(s3.accessKey())) {
                missing.add("qinglan.storage.s3.access-key（QINGLAN_S3_ACCESS_KEY）");
            }
            if (isBlank(s3.secretKey())) {
                missing.add("qinglan.storage.s3.secret-key（QINGLAN_S3_SECRET_KEY）");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("qinglan.storage.type=s3 但对象存储配置不完整，已拒绝启动。"
                    + "缺失：" + String.join("、", missing)
                    + "。请在 .env 中补齐后重启（可对接自建 MinIO / 腾讯 COS / 阿里 OSS）。");
        }
        return s3;
    }

    private static String regionOf(StorageProperties.S3 s3) {
        return isBlank(s3.region()) ? DEFAULT_REGION : s3.region();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
