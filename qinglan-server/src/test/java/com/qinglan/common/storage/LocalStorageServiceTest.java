package com.qinglan.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 本地磁盘实现的存取删与路径安全（两种存储实现共用同一 URL 契约，见 {@link StorageService}）。
 */
@DisplayName("本地磁盘存储实现")
class LocalStorageServiceTest {

    @TempDir
    Path root;

    private LocalStorageService storage() {
        return new LocalStorageService(root);
    }

    @Test
    @DisplayName("store 落盘并返回 /uploads 形态地址，exists/load/delete 闭环")
    void shouldStoreLoadDelete() {
        LocalStorageService storage = storage();
        byte[] content = {1, 2, 3};

        String url = storage.store(content, "jpg");

        assertThat(url).matches("^/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.jpg$");
        assertThat(storage.exists(url)).isTrue();
        StoredObject loaded = storage.load(url).orElseThrow();
        assertThat(loaded.content()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(loaded.etag()).isNotBlank();

        storage.delete(url);
        assertThat(storage.exists(url)).isFalse();
        assertThat(storage.load(url)).isEmpty();
    }

    @Test
    @DisplayName("穿越与非法地址一律视为不存在，且 delete 不会碰到目录外")
    void shouldRejectTraversal() {
        LocalStorageService storage = storage();

        List<String> bad = List.of(
                "../secret.jpg",
                "/uploads/../../secret.jpg",
                "/other/x.jpg",
                "not-a-url",
                "/uploads");

        for (String address : bad) {
            assertThat(storage.exists(address)).as("exists(%s)", address).isFalse();
            assertThat(storage.load(address)).as("load(%s)", address).isEmpty();
            assertThatCode(() -> storage.delete(address)).doesNotThrowAnyException();
        }
    }
}
