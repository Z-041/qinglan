package com.qinglan.common.store;

import java.time.Duration;

/**
 * 短时效键值存储抽象：计数与一次性占位。
 *
 * <p>键由调用方按 AGENTS.md 的 {@code ql:{domain}:{id}} 约定拼好，存储层不感知业务语义，
 * 因此同一套实现可以同时服务于「上传限流」与「一次性令牌消费」这类场景。</p>
 */
public interface EphemeralStore {

    /** 在 ttl 窗口内累加计数并返回累加后的值；首次写入时设置过期时间，避免永久残留 */
    long increment(String key, Duration ttl);

    /** 首次调用占用该键并返回 true；ttl 内重复调用返回 false（用于一次性令牌的消费） */
    boolean markUsedOnce(String key, Duration ttl);
}
