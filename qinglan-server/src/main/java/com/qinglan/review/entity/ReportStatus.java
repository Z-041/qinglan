package com.qinglan.review.entity;

/**
 * 举报的处理状态。
 */
public enum ReportStatus {
    /** 待处理：进入审核队列，计入目标帖子的待处理举报数 */
    PENDING,
    /** 举报成立：内容已被处置（下架/移除/自动降级） */
    UPHELD,
    /** 驳回举报：经审核内容不违规，目标保留 */
    DISMISSED
}
