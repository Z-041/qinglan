package com.qinglan.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 存储实现的装配：默认本地、type=s3 且缺配置时启动期失败（给可操作的中文原因）。
 */
class StorageConfigTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("type 缺省（老配置没有本项）时用本地磁盘实现，不报错")
    void shouldDefaultToLocal() {
        StorageService storage = new StorageConfig(
                new StorageProperties(null, root.toString(), null, null, null)).storageService();

        assertThat(storage).isInstanceOf(LocalStorageService.class);
    }

    @Test
    @DisplayName("type=s3 但缺 endpoint/bucket/凭据：启动失败，消息列出具体配置键与环境变量")
    void shouldFailFastWhenS3ConfigIncomplete() {
        StorageProperties.S3 empty = new StorageProperties.S3("", "", "", "", "");
        StorageProperties properties = new StorageProperties("s3", null, empty, null, null);

        assertThatThrownBy(() -> new StorageConfig(properties).storageService())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qinglan.storage.type=s3")
                .hasMessageContaining("QINGLAN_S3_ENDPOINT")
                .hasMessageContaining("QINGLAN_S3_BUCKET")
                .hasMessageContaining("QINGLAN_S3_ACCESS_KEY")
                .hasMessageContaining("QINGLAN_S3_SECRET_KEY");
    }

    @Test
    @DisplayName("type=s3 且 s3 段整体缺失时同样启动失败（不是 NPE）")
    void shouldFailFastWhenS3SectionMissing() {
        assertThatThrownBy(() -> new StorageConfig(
                new StorageProperties("s3", null, null, null, null)).storageService())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QINGLAN_S3_ENDPOINT");
    }
}
