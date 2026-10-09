package com.qinglan.common.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * S3 兼容客户端构造。抽出来单独一处，是为了让集成测试能用**同一段配置代码**指向内嵌 S3Mock，
 * 从而真正验证 path-style 等设置，而不是在测试里另建一个「看起来也像」的 client。
 *
 * <p>{@code forcePathStyle=true} 是自建 MinIO 的硬要求：默认的 virtual-host 风格会拼出
 * {@code http://bucket.endpoint/key}，MinIO 单机模式下解析不了。</p>
 */
final class S3ClientFactory {

    private S3ClientFactory() {
    }

    static S3Client create(String endpoint, String region, String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
