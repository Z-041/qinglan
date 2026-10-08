package com.qinglan.review.entity;

/**
 * 提案状态（持久化名）。合法迁移由 {@link ProposalState} 裁决，实体不得绕过状态机直接赋值。
 *
 * <p>只有四个状态，且**没有**「超管已裁决」这种状态：超管裁决与表决通过都是「处置准予」，
 * 区别只在 {@code resolved_by} 与 {@code resolution}——多一个状态就多一处要判的分支
 * （与「状态能少不多」的取向一致，见方案 15.x 偏差说明）。</p>
 */
public enum ProposalStatus {
    /** 进行中：可投票，达到或不可能达到阈值时结案 */
    OPEN,
    /** 准予处置：表决达到阈值，或超管裁决准予 */
    APPROVED,
    /** 不予处置：超管裁决驳回，或结案前目标状态已变导致处置无法执行 */
    REJECTED,
    /** 僵局：同意票已不可能过半，等超管裁决 */
    ESCALATED
}
