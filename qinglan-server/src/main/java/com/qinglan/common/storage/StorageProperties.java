package com.qinglan.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件存储配置。
 *
 * @param type                实现类型：{@code local}（本地磁盘，默认）或 {@code s3}（S3 兼容对象存储）
 * @param localRoot           本地存储根目录，仅在 type=local 时生效
 * @param s3                  S3 兼容存储参数，仅在 type=s3 时生效；缺失时启动失败（见 StorageConfig）
 * @param imageSigningSecret  私有图片签名 URL 的 HMAC 密钥。留空时回落 {@code qinglan.jwt.secret}
 *                            （**权宜**，仅为本地/测试便利）；生产必须显式配置，见 ProdEnvironmentGuard
 * @param imageSigningTtl     签名有效期；留空取默认 10 分钟，硬上限 1 小时（见 ImageUrlSigner）
 */
@ConfigurationProperties(prefix = "qinglan.storage")
public record StorageProperties(String type, String localRoot, S3 s3,
                                String imageSigningSecret, Duration imageSigningTtl) {

    /**
     * S3 兼容存储参数。四项必填（缺失即启动失败并给出可操作的中文原因）；
     * {@code region} 可留空，默认 {@code us-east-1}（MinIO 等自建服务不校验区域）。
     *
     * <p>凭据只允许环境变量注入，禁止写进代码或镜像（AGENTS.md 第九章）。</p>
     */
    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
    }
}
