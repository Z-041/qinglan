package com.qinglan.post.dto;

import com.qinglan.post.entity.PostStatus;

import java.time.Instant;
import java.util.List;

/**
 * 「我的帖子」列表项（本人视角）。
 *
 * <p>与 {@link PostSummaryResponse} 的差别：多带 {@code status} 与 {@code reviewReason}，
 * 覆盖待审 / 已驳回 / 已下架 / 已通过全部状态。{@code reviewReason} <b>仅本人可见</b>——
 * 这张列表本身就只有本人能访问（按 {@code authorId} 过滤），与详情接口的语义一致。</p>
 *
 * <p>可含 {@code anonymousCode}（本人视角，与既有「我的帖子」语义一致），但**不含他人身份**：
 * 没有 authorId、没有邮箱、没有学号。</p>
 */
public record MyPostItemResponse(
        Long id,
        Long topicId,
        String topicName,
        String content,
        List<String> images,
        String anonymousCode,
        PostStatus status,
        String reviewReason,
        long likeCount,
        int commentCount,
        Instant createdAt
) {
}
