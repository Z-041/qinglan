package com.qinglan.common.web;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CompositeCursor.KeyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 复合游标的解析 / 格式化 / 校验规则。
 */
class CompositeCursorTest {

    @Test
    @DisplayName("合法游标按段序解析为各段值")
    void parsesValidCursor() {
        assertThat(CompositeCursor.parse("12:345", KeyKind.MICROS, KeyKind.POSITIVE_LONG))
                .containsExactly(12L, 345L);
        assertThat(CompositeCursor.parse("0:9", KeyKind.NON_NEGATIVE_LONG, KeyKind.POSITIVE_LONG))
                .containsExactly(0L, 9L);
        assertThat(CompositeCursor.parse("7:8:0", KeyKind.POSITIVE_LONG, KeyKind.MICROS, KeyKind.NON_NEGATIVE_INT))
                .containsExactly(7L, 8L, 0L);
    }

    @Test
    @DisplayName("format 与 parse 互逆，段序一致")
    void formatRoundTrips() {
        String cursor = CompositeCursor.format(1700000000000000L, 42L, 1L);
        assertThat(cursor).isEqualTo("1700000000000000:42:1");
        assertThat(CompositeCursor.parse(cursor, KeyKind.MICROS, KeyKind.POSITIVE_LONG, KeyKind.NON_NEGATIVE_INT))
                .containsExactly(1700000000000000L, 42L, 1L);
    }

    @Test
    @DisplayName("空白游标视为非法")
    void rejectsBlank() {
        assertInvalid(() -> CompositeCursor.parse(null, KeyKind.MICROS, KeyKind.POSITIVE_LONG));
        assertInvalid(() -> CompositeCursor.parse("   ", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
    }

    @Test
    @DisplayName("段数不符视为非法")
    void rejectsWrongSegmentCount() {
        assertInvalid(() -> CompositeCursor.parse("12", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
        assertInvalid(() -> CompositeCursor.parse("12:34:56", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
    }

    @Test
    @DisplayName("非数字段视为非法")
    void rejectsNonNumeric() {
        assertInvalid(() -> CompositeCursor.parse("abc:34", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
        assertInvalid(() -> CompositeCursor.parse("12:x", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
    }

    @Test
    @DisplayName("越界段视为非法：MICROS/POSITIVE_LONG 必须为正，NON_NEGATIVE_LONG 可为零，NON_NEGATIVE_INT 限 int 范围")
    void rejectsOutOfRange() {
        assertInvalid(() -> CompositeCursor.parse("0:34", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
        assertInvalid(() -> CompositeCursor.parse("12:0", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
        assertInvalid(() -> CompositeCursor.parse("-1:34", KeyKind.MICROS, KeyKind.POSITIVE_LONG));
        assertInvalid(() -> CompositeCursor.parse("12:-1", KeyKind.MICROS, KeyKind.NON_NEGATIVE_LONG));
        assertInvalid(() -> CompositeCursor.parse(
                (Integer.MAX_VALUE + 1L) + ":34", KeyKind.NON_NEGATIVE_INT, KeyKind.POSITIVE_LONG));

        // 边界内合法
        assertThat(CompositeCursor.parse("0:34", KeyKind.NON_NEGATIVE_LONG, KeyKind.POSITIVE_LONG))
                .containsExactly(0L, 34L);
        assertThat(CompositeCursor.parse(Integer.MAX_VALUE + ":34", KeyKind.NON_NEGATIVE_INT, KeyKind.POSITIVE_LONG))
                .containsExactly((long) Integer.MAX_VALUE, 34L);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
    }
}
