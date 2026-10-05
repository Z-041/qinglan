package com.qinglan.auth.store;

import java.time.Duration;
import java.util.Optional;

/**
 * 验证码短期存储抽象。
 *
 * <p>键一律使用邮箱的不可逆哈希，存储层不会出现邮箱明文。</p>
 */
public interface VerificationCodeStore {

    void saveCode(String emailHash, String code, Duration ttl);

    Optional<String> findCode(String emailHash);

    /**
     * 累加失败次数并返回累加后的值。计数键在首次写入时设置过期时间，避免永久残留。
     */
    long incrementAttempts(String emailHash, Duration ttl);

    void removeCode(String emailHash);

    /**
     * 申请一次发送配额（同一邮箱在 interval 内只允许成功一次）。
     */
    boolean tryAcquireSendSlot(String emailHash, Duration interval);
}
