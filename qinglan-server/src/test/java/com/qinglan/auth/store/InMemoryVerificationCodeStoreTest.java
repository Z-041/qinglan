package com.qinglan.auth.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存验证码存储的单元测试（默认 code-store=memory，即本地与测试环境实际走这条路径）。
 *
 * <p>重点是发送间隔的**原子性**：原实现是「先查是否过期、再 put」两步，两个并发请求会同时
 * 看到旧槽位已过期、各自覆盖后都返回 true —— 同一邮箱双发验证码。</p>
 */
class InMemoryVerificationCodeStoreTest {

    private static final String EMAIL_HASH = "hash-of-someone@qinglan.edu.cn";

    private final InMemoryVerificationCodeStore store = new InMemoryVerificationCodeStore();

    @Test
    @DisplayName("首次获取槽位成功，间隔内再次获取被拒")
    void shouldRejectSecondSendWithinInterval() {
        assertThat(store.tryAcquireSendSlot(EMAIL_HASH, Duration.ofSeconds(60))).isTrue();
        assertThat(store.tryAcquireSendSlot(EMAIL_HASH, Duration.ofSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("不同邮箱互不影响")
    void shouldNotAffectOtherEmails() {
        assertThat(store.tryAcquireSendSlot("a", Duration.ofSeconds(60))).isTrue();
        assertThat(store.tryAcquireSendSlot("b", Duration.ofSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("槽位过期后可再次获取")
    void shouldAllowAfterExpiry() throws InterruptedException {
        assertThat(store.tryAcquireSendSlot(EMAIL_HASH, Duration.ofMillis(1))).isTrue();
        Thread.sleep(5);

        assertThat(store.tryAcquireSendSlot(EMAIL_HASH, Duration.ofMillis(1))).isTrue();
    }

    @Test
    @DisplayName("槽位过期后两个并发请求只能有一个成功：防止同一邮箱双发验证码")
    void shouldAllowOnlyOneConcurrentAcquireAfterExpiry() throws Exception {
        // 先占一个已过期的槽位：两个线程都会看到「已过期」，这正是原实现出错的时机
        assertThat(store.tryAcquireSendSlot(EMAIL_HASH, Duration.ofMillis(1))).isTrue();
        Thread.sleep(5);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> acquire = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return store.tryAcquireSendSlot(EMAIL_HASH, Duration.ofSeconds(60));
            };
            List<Future<Boolean>> results = List.of(pool.submit(acquire), pool.submit(acquire));
            List<Boolean> outcomes = List.of(results.get(0).get(5, TimeUnit.SECONDS),
                    results.get(1).get(5, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Boolean.TRUE::equals).count())
                    .as("并发下必须恰好一个线程拿到槽位")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
