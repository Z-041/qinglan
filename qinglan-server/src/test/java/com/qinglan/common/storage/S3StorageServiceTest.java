package com.qinglan.common.storage;

import com.adobe.testing.s3mock.junit5.S3MockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * S3 兼容实现的集成测试，跑在**内嵌 S3Mock** 上。
 *
 * <p>选 S3Mock（纯 Java、无需 Docker）是因为本机没有 Docker、起不了 MinIO，而 AGENTS.md 第八章
 * 要求测试用真实依赖不 mock 行为——S3Mock 提供的是真实 S3 HTTP 协议实现，够验证
 * put/get/delete 与 path-style。真实云存储（MinIO / COS / OSS）未在本机验证。</p>
 */
@DisplayName("S3 兼容存储实现（内嵌 S3Mock）")
class S3StorageServiceTest {

    private static final String BUCKET = "qinglan-test";
    /** 任意字节即可：本测试只验证存储行为，不涉及图片类型识别 */
    private static final byte[] CONTENT = HexFormat.of()
            .parseHex("ffd8ffe000104a46494600010100000100010000");

    @RegisterExtension
    static final S3MockExtension S3_MOCK = S3MockExtension.builder()
            .silent()
            .withSecureConnection(false)
            .withInitialBuckets(BUCKET)
            .build();

    private S3StorageService storage;

    @BeforeEach
    void setUp() {
        // 用与生产同一段构造代码（S3ClientFactory）指向内嵌服务，path-style 等设置随之被真实验证
        S3Client client = S3ClientFactory.create(S3_MOCK.getServiceEndpoint(), "us-east-1", "test", "test");
        storage = new S3StorageService(client, BUCKET);
    }

    @Test
    @DisplayName("store 返回 /uploads/YYYY/MM/uuid.ext 形态，且能按同一地址读回内容")
    void shouldStoreAndLoad() {
        String url = storage.store(CONTENT, "jpg");

        assertThat(url).matches("^/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.jpg$");
        // 对象 key 与 URL 去掉 /uploads/ 前缀后的路径段一致（图片下发靠它反查归属）
        assertThat(StorageUrl.objectKey(url)).isPresent();

        assertThat(storage.exists(url)).isTrue();
        StoredObject loaded = storage.load(url).orElseThrow();
        assertThat(loaded.content()).isEqualTo(CONTENT);
        assertThat(loaded.etag()).isNotBlank();
    }

    @Test
    @DisplayName("delete 之后 exists=false、load 为空")
    void shouldDelete() {
        String url = storage.store(CONTENT, "png");

        storage.delete(url);

        assertThat(storage.exists(url)).isFalse();
        assertThat(storage.load(url)).isEmpty();
    }

    @Test
    @DisplayName("删除不存在/非法地址幂等且不抛异常（失败不阻断主流程）")
    void deleteIsIdempotent() {
        String missing = "/uploads/2026/10/" + "a".repeat(32) + ".jpg";

        assertThatCode(() -> storage.delete(missing)).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete("/uploads/anything")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("不存在的对象：exists=false、load 为空")
    void missingObject() {
        String missing = "/uploads/2026/10/" + "b".repeat(32) + ".jpg";

        assertThat(storage.exists(missing)).isFalse();
        assertThat(storage.load(missing)).isEmpty();
        assertThat(storage.exists("../secret")).isFalse();
    }
}
