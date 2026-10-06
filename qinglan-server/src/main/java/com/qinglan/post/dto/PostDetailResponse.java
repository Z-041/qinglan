package com.qinglan.post.dto;

import java.time.Instant;
import java.util.List;

/**
 * 帖子详情。
 *
 * <p>联系方式脱敏在服务端完成（不变量 #2）：未认证 viewer 拿到的 {@link #contact()} 必然是
 * {@code null}，不是靠前端 {@code v-if} 隐藏。</p>
 *
 * @param contactVisible 未认证或被处罚隐藏时为 false，前端据此显示「认证后可查看联系方式」
 * @param reviewReason   驳回/下架理由，仅作者本人可见，其余为 null
 * @param takenDown      是否处于下架态。下架帖只有作者能打开，作者据此获得申诉入口；
 *                       不能用 {@code reviewReason} 非空代替——驳回、下架、以及恢复后的帖子都会残留该字段
 */
public record PostDetailResponse(
        Long id,
        Long topicId,
        String topicName,
        String topicRules,
        String content,
        String contact,
        boolean contactVisible,
        List<String> images,
        String anonymousCode,
        long likeCount,
        boolean liked,
        int commentCount,
        boolean mine,
        String reviewReason,
        boolean takenDown,
        Instant createdAt
) {
}
