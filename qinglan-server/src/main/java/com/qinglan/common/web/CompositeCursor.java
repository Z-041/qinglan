package com.qinglan.common.web;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;

/**
 * 复合游标（{@code a:b:c}）的解析与格式化。
 *
 * <p>游标对客户端不透明，只要求原样回传；服务端用它定位「上一页最后一条」。各列表的游标形态不同
 * （微秒时间戳 + id、热门分数 + id、举报数 + 微秒 + id + 类型序…），但「按段拆分 → 段数校验 →
 * 逐段解析 → 逐段取值校验 → 非法即 {@link ErrorCode#CURSOR_INVALID}」这套模板此前在多个 Service
 * 里各写一遍，且各存一套分隔符 / 段数常量。这里按**键规格**收敛成一份。</p>
 *
 * <p>本类只做「解析 / 格式化 / 校验」，<b>不决定首页语义</b>：无游标（首页）各列表的默认值不同
 * （极值哨兵 / {@code null} / 时间下界），由调用方在进来之前自行处理。微秒与
 * {@link java.time.Instant} 的换算仍归 {@link CursorCodec}。</p>
 */
public final class CompositeCursor {

    /** 段分隔符：全仓库游标统一用冒号 */
    private static final String SEPARATOR = ":";

    /** 单段键的取值语义与校验规则 */
    public enum KeyKind {
        /** 微秒时间戳：必须 > 0 */
        MICROS {
            @Override
            boolean accepts(long value) {
                return value > 0;
            }
        },
        /** 正整数（id、计数）：必须 > 0 */
        POSITIVE_LONG {
            @Override
            boolean accepts(long value) {
                return value > 0;
            }
        },
        /** 非负整数（热门分数等，可为 0）：必须 >= 0 */
        NON_NEGATIVE_LONG {
            @Override
            boolean accepts(long value) {
                return value >= 0;
            }
        },
        /** 非负 int（目标类型序）：0 到 {@link Integer#MAX_VALUE} */
        NON_NEGATIVE_INT {
            @Override
            boolean accepts(long value) {
                return value >= 0 && value <= Integer.MAX_VALUE;
            }
        };

        abstract boolean accepts(long value);
    }

    private CompositeCursor() {
    }

    /**
     * 按给定键规格解析游标。
     *
     * @return 各段的值，顺序与 {@code kinds} 一致
     * @throws BizException {@link ErrorCode#CURSOR_INVALID}：游标为空白、段数不符、任一段非数字或越界
     */
    public static long[] parse(String raw, KeyKind... kinds) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String[] parts = raw.trim().split(SEPARATOR);
        if (parts.length != kinds.length) {
            throw invalid();
        }
        long[] values = new long[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            long value;
            try {
                value = Long.parseLong(parts[i]);
            } catch (NumberFormatException ex) {
                throw invalid();
            }
            if (!kinds[i].accepts(value)) {
                throw invalid();
            }
            values[i] = value;
        }
        return values;
    }

    /** 把各段拼成游标；与 {@link #parse} 的段序一致 */
    public static String format(long... values) {
        StringBuilder cursor = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                cursor.append(SEPARATOR);
            }
            cursor.append(values[i]);
        }
        return cursor.toString();
    }

    private static BizException invalid() {
        return new BizException(ErrorCode.CURSOR_INVALID);
    }
}
