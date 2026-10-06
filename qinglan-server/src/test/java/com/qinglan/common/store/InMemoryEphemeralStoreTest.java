package com.qinglan.common.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 短时效存储的内存实现：计数累加与一次性占位。
 */
class InMemoryEphemeralStoreTest {

    private final InMemoryEphemeralStore store = new InMemoryEphemeralStore();

    @Test
    @DisplayName("窗口内计数累加，超出后由调用方判定拒绝")
    void shouldAccumulateCountWithinWindow() {
        Duration window = Duration.ofMinutes(1);

        assertThat(store.increment("ql:upload:rate:7", window)).isEqualTo(1);
        assertThat(store.increment("ql:upload:rate:7", window)).isEqualTo(2);
        // 不同 key 互不影响
        assertThat(store.increment("ql:upload:rate:8", window)).isEqualTo(1);
    }

    @Test
    @DisplayName("一次性占位：首次成功，重复失败")
    void shouldClaimKeyOnlyOnce() {
        String key = "ql:auth:emailtoken:abc";

        assertThat(store.markUsedOnce(key, Duration.ofMinutes(10))).isTrue();
        assertThat(store.markUsedOnce(key, Duration.ofMinutes(10))).isFalse();
    }

    @Test
    @DisplayName("过期条目视为不存在，可被重新占用")
    void shouldReclaimExpiredKey() {
        String key = "ql:auth:emailtoken:expired";

        // TTL 为零即写入瞬间过期，用来确定性地覆盖过期分支
        assertThat(store.markUsedOnce(key, Duration.ZERO)).isTrue();
        assertThat(store.markUsedOnce(key, Duration.ofMinutes(10))).isTrue();
    }
}
