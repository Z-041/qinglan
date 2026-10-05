package com.qinglan.comment.dto;

import java.time.Instant;
import java.util.List;

/**
 * 评论。一级评论的 {@link #replies()} 非空，回复的为空列表。
 *
 * <p>同 {@code PostSummaryResponse}：不返回 authorId，只用匿名代号 + {@code mine}（不变量 #1）。</p>
 */
public record CommentResponse(
        Long id,
        String content,
        String anonymousCode,
        long likeCount,
        boolean liked,
        boolean mine,
        Instant createdAt,
        List<CommentResponse> replies
) {
}
