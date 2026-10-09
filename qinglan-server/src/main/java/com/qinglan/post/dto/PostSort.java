package com.qinglan.post.dto;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;

import java.util.Locale;

/**
 * 信息流排序口径。
 *
 * <p>两种排序的**游标格式不同**（{@code latest} 是单个帖子 id，{@code hot} 是「热度分:id」复合值），
 * 前端切换排序时必须一并回传 {@code sort}，服务端据此解析游标。</p>
 */
public enum PostSort {

    /** 默认：按帖子 id 倒序（等价于发布时间倒序），游标为单个 id，与既有行为完全一致 */
    LATEST,

    /** 热门：按「点赞×1 + 评论×2」乘时间衰减排序，见 {@code PostService} 的口径说明 */
    HOT;

    /**
     * 解析查询参数 {@code sort}（大小写不敏感，前端用 {@code hot} / {@code latest}）。
     *
     * <p>未知取值按非法请求拒绝，而不是静默回退默认——把前端拼错的参数悄悄当成最新排序，
     * 会让人以为「热门排序没效果」而查错方向。</p>
     */
    public static PostSort fromRequest(String raw) {
        if (raw == null || raw.isBlank()) {
            return LATEST;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "latest" -> LATEST;
            case "hot" -> HOT;
            default -> throw new BizException(ErrorCode.INVALID_REQUEST);
        };
    }
}
