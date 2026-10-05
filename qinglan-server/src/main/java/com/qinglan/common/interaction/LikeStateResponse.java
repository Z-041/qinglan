package com.qinglan.common.interaction;

/**
 * 点赞结果。点赞 / 取消点赞都返回它，前端直接用它覆盖本地状态。
 */
public record LikeStateResponse(long likeCount, boolean liked) {
}
