package com.qinglan.announcement.dto;

import jakarta.validation.constraints.Positive;

/**
 * 标记公告已读的入参。强制阅读必须真实计时：{@code durationMs} 小于公告要求的最少秒数时服务端拒绝落库（不变量 #6）。
 *
 * @param deviceId     游客设备标识；登录用户可省略。与查询参数二选一，请求体优先
 * @param durationMs   真实停留时长（毫秒），必须为正
 * @param scrolledToEnd 是否已滚动到底（客户端信号，服务端无法复核，仅作证据落库）
 * @param confirmed    是否已勾选「已了解」（同上）
 */
public record MarkReadRequest(
        String deviceId,

        @Positive(message = "停留时长必须为正")
        long durationMs,

        boolean scrolledToEnd,

        boolean confirmed
) {
}
