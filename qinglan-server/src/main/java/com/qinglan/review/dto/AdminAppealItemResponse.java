package com.qinglan.review.dto;

import com.qinglan.review.entity.ReviewTargetType;

import java.time.Instant;

/**
 * 待复审申诉队列的一行（管理端）。
 *
 * <p>刻意不含 {@code appellantId}——申诉人就是作者，作者身份不出队列（不变量 #1 的引申，
 * 与 {@link ReviewQueueItemResponse} 同口径）。管理端只看到「内容 + 原判 + 申诉理由」。</p>
 *
 * @param appealId        复审接口（accept / reject）所需；界面不渲染这个数字（决策 6）
 * @param targetId        供管理端按 target 打开上下文面板
 * @param preview         帖子内容摘要，超长已截断，与举报队列同口径
 * @param targetStatus    帖子当前状态码
 * @param takenDownReason 原下架理由（{@code post.review_reason}），供复审人判断原判是否合理
 * @param appealReason    作者填写的申诉理由
 * @param appealedAt      申诉提交时间
 */
public record AdminAppealItemResponse(
        Long appealId,
        ReviewTargetType targetType,
        Long targetId,
        String preview,
        String targetStatus,
        String takenDownReason,
        String appealReason,
        Instant appealedAt
) {
}
