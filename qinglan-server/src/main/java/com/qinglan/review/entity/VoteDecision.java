package com.qinglan.review.entity;

/**
 * 投票选项。
 *
 * <p>刻意**没有弃权**：不投就是弃权，且未投票者计入分母（方案 5.2）——否则拉几个人弃权就能
 * 变相放行。想表达「内容没问题」就投不同意。</p>
 */
public enum VoteDecision {
    AGREE,
    DISAGREE
}
