package com.qinglan.common.web;

import java.util.List;

/**
 * 游标分页结果。
 *
 * <p>AGENTS.md 第三章：列表接口一律用游标，不得用 {@code page/size}。游标是上一页最后一条的 id，
 * 新数据插入不会导致翻页错位，也避免了大偏移量的深分页开销。</p>
 *
 * @param items      当前页数据
 * @param nextCursor 下一页游标；为 {@code null} 表示已到末页
 */
public record CursorPage<T>(List<T> items, Long nextCursor) {

    public static <T> CursorPage<T> of(List<T> items, Long nextCursor) {
        return new CursorPage<>(items, nextCursor);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null);
    }
}
