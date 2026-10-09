package com.qinglan.review.repository;

import com.qinglan.review.entity.ReportReason;

/**
 * 举报按理由分组的计数投影（看板「举报按理由分布」，方案阶段 C）。
 *
 * <p>只在 {@code review} 模块内部流转，不直接序列化；装配成 DTO 后出参。</p>
 */
public record ReportReasonCount(ReportReason reason, long count) {
}
