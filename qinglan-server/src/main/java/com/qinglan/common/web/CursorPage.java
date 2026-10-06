package com.qinglan.common.web;

import java.util.List;

/**
 * 游标分页结果。
 *
 * <p>AGENTS.md 第三章：列表接口一律用游标，不得用 {@code page/size}。游标是上一页最后一条的位置，
 * 新数据插入不会导致翻页错位，也避免了大偏移量的深分页开销。</p>
 *
 * <p>游标对客户端是**不透明**的：只要求原样回传，不解析。因此类型定为 {@code String}——
 * 信息流装的是帖子 id，审核队列装的是「举报数:首报时间:目标 id」这样的复合键，
 * 两者共用本类型而不必各自造一套分页包装。</p>
 *
 * @param items      当前页数据
 * @param nextCursor 下一页游标；为 {@code null} 表示已到末页
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, nextCursor);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null);
    }
}
