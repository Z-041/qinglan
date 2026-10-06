package com.qinglan.common.moderation;

/**
 * 内容审核判定结果。
 *
 * @param allowed 是否放行
 * @param reason  拒绝原因，放行时为 {@code null}；只用于服务端日志，不直接回显给用户
 */
public record ModerationResult(boolean allowed, String reason) {

    public static ModerationResult pass() {
        return new ModerationResult(true, null);
    }

    public static ModerationResult reject(String reason) {
        return new ModerationResult(false, reason);
    }
}
