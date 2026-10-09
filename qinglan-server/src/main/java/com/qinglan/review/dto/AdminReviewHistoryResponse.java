package com.qinglan.review.dto;

import com.qinglan.common.audit.AuditAction;

import java.time.Instant;
import java.util.List;

/**
 * 上下文面板的增强信息（阶段 A）：作者最近发帖 + 目标处置流水。
 *
 * <p><b>只回 ID 与状态 / 动作，不含学号、邮箱、匿名代号</b>（不变量 #1 / #3）。作者只以「帖子
 * 的 id 与状态」出现，仍无法关联到具体人；处置流水只给 operatorId（管理端本来就按 userId 操作）。</p>
 */
public record AdminReviewHistoryResponse(
        List<AuthorPostItem> authorRecentPosts,
        List<DispositionItem> targetDispositions
) {

    /**
     * 作者最近的一条帖子。
     *
     * @param status 帖子审核状态（{@code PostStatus} 持久化名），前端按码映射文案
     */
    public record AuthorPostItem(Long id, String status, Instant createdAt) {
    }

    /**
     * 目标的一条处置流水（来自只增不改删的 {@code review_log}）。
     *
     * @param operatorId 处置人 userId；{@code 0} 为系统自动处置的哨兵值
     * @param reason     处置理由原文，可能为空
     */
    public record DispositionItem(AuditAction action, Long operatorId, String reason, Instant createdAt) {
    }
}
