package com.qinglan.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 游标时间编解码的单元测试：核心是「微秒不能丢」——毫秒截断正是翻页重复行的根因。
 */
class CursorCodecTest {

    @Test
    @DisplayName("往返保持微秒精度，非整毫秒的时间不被截断")
    void shouldRoundTripMicros() {
        Instant original = Instant.parse("2026-10-06T00:00:00.234567Z");

        long micros = CursorCodec.encode(original);

        assertThat(micros).isEqualTo(1_791_244_800_234_567L);
        assertThat(CursorCodec.decode(micros)).isEqualTo(original);
    }

    @Test
    @DisplayName("同一毫秒内的两个时刻编出的游标必须不同（毫秒截断会失败）")
    void shouldDistinguishInstantsWithinTheSameMillisecond() {
        Instant first = Instant.parse("2026-10-06T00:00:00.000100Z");
        Instant second = Instant.parse("2026-10-06T00:00:00.000900Z");

        assertThat(first.toEpochMilli()).isEqualTo(second.toEpochMilli());
        assertThat(CursorCodec.encode(first)).isNotEqualTo(CursorCodec.encode(second));
    }

    @Test
    @DisplayName("EPOCH 与整秒时间往返正确")
    void shouldHandleEpochAndWholeSeconds() {
        assertThat(CursorCodec.encode(Instant.EPOCH)).isZero();
        assertThat(CursorCodec.decode(0L)).isEqualTo(Instant.EPOCH);

        Instant whole = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(CursorCodec.decode(CursorCodec.encode(whole))).isEqualTo(whole);
    }
}
