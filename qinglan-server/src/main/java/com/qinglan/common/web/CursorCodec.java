package com.qinglan.common.web;

import java.time.Instant;

/**
 * 游标里的时间编解码：**微秒精度**。
 *
 * <p>时间列是 {@code DATETIME(6)}（微秒）。游标若只带毫秒，键集分页在边界行与
 * 后续行同毫秒时会重复取行——上一个页面的末条被截断成更早的时间，下一页的
 * {@code min(created_at) > 游标} 对这些行恒真。故游标必须与列同精度。</p>
 *
 * <p>本类只做「微秒精度 {@code Instant} ↔ {@code long}」的换算，<b>不含任何校验</b>：
 * 非法游标的拒绝仍由各 Service 现有的段数 / 正负检查负责，行为与位置都不变。</p>
 */
public final class CursorCodec {

    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1_000L;

    private CursorCodec() {
    }

    /** {@code Instant} → 自 epoch 起的微秒数 */
    public static long encode(Instant instant) {
        return instant.getEpochSecond() * MICROS_PER_SECOND + instant.getNano() / NANOS_PER_MICRO;
    }

    /** 自 epoch 起的微秒数 → {@code Instant} */
    public static Instant decode(long micros) {
        return Instant.ofEpochSecond(Math.floorDiv(micros, MICROS_PER_SECOND),
                Math.floorMod(micros, MICROS_PER_SECOND) * NANOS_PER_MICRO);
    }
}
