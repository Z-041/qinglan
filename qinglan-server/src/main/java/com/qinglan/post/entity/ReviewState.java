package com.qinglan.post.entity;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 审核状态机（AGENTS.md 不变量 #5）。
 *
 * <p>本接口是状态迁移合法性的**唯一裁决者**，实体不得绕过它直接改 {@link PostStatus}。
 * 允许的迁移只有：</p>
 *
 * <pre>
 * Draft → Pending → Reviewing → Approved / Rejected
 * Approved → TakenDown → Approved（申诉通过）
 * </pre>
 *
 * <p>禁止跳态（如 Pending 直接到 Approved），禁止 TakenDown 直接回到 Pending。
 * 新增状态必须改本接口的 {@code permits} 列表并补测试。</p>
 */
public sealed interface ReviewState
        permits ReviewState.Draft,
                ReviewState.Pending,
                ReviewState.Reviewing,
                ReviewState.Approved,
                ReviewState.Rejected,
                ReviewState.TakenDown {

    /** 该状态落库时写入的取值 */
    PostStatus status();

    /** 合法迁移表：key 为当前状态，value 为允许到达的状态 */
    Map<PostStatus, Set<PostStatus>> ALLOWED_TRANSITIONS = Map.of(
            PostStatus.DRAFT, Set.of(PostStatus.PENDING),
            PostStatus.PENDING, Set.of(PostStatus.REVIEWING),
            PostStatus.REVIEWING, Set.of(PostStatus.APPROVED, PostStatus.REJECTED),
            PostStatus.APPROVED, Set.of(PostStatus.TAKEN_DOWN),
            PostStatus.TAKEN_DOWN, Set.of(PostStatus.APPROVED),
            PostStatus.REJECTED, Set.of()
    );

    /** 草稿：尚未提交审核，只有作者可见 */
    record Draft() implements ReviewState {
        @Override
        public PostStatus status() {
            return PostStatus.DRAFT;
        }
    }

    /** 已提交，等待机审/人审 */
    record Pending(Instant submittedAt, RiskLevel risk) implements ReviewState {
        @Override
        public PostStatus status() {
            return PostStatus.PENDING;
        }
    }

    /** 审核中，已被某位审核员认领（P2 起由队列驱动） */
    record Reviewing(Long reviewerId, Instant claimedAt) implements ReviewState {
        @Override
        public PostStatus status() {
            return PostStatus.REVIEWING;
        }
    }

    /** 审核通过，对外可见 */
    record Approved(Long reviewerId, Instant approvedAt) implements ReviewState {
        @Override
        public PostStatus status() {
            return PostStatus.APPROVED;
        }
    }

    /** 驳回，仅作者可见 */
    record Rejected(String reasonCode, String explanation) implements ReviewState {
        @Override
        public PostStatus status() {
            return PostStatus.REJECTED;
        }
    }

    /** 下架，仅作者可见；申诉通过可回到 Approved */
    record TakenDown(Long operatorId, String reason, Instant at) implements ReviewState {
        @Override
        public PostStatus status() {
            return PostStatus.TAKEN_DOWN;
        }
    }

    /**
     * 校验迁移是否合法，非法即抛 {@link ErrorCode#ILLEGAL_REVIEW_TRANSITION}（不变量 #5：禁止跳态）。
     */
    static void assertTransition(PostStatus from, PostStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
    }

    static boolean canTransition(PostStatus from, PostStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
