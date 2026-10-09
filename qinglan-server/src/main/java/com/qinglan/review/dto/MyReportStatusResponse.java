package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportStatus;

import java.time.Instant;

/**
 * 当前登录用户在某个目标上的举报状态（学生端详情页「举报反馈」）。
 *
 * <p>只描述<b>调用者自己</b>那一条举报：没有 reporterId、没有举报条数、没有他人举报的任何字段
 * （不变量 #1 的引申——举报人身份不出审核队列，也不能通过本接口反查他人是否举报过）。
 * 未举报时 {@code status} / {@code createdAt} / {@code handledAt} 均为 {@code null}。</p>
 *
 * <p>报告进度取三种状态：{@code PENDING} 处理中、{@code UPHELD} 举报成立、{@code DISMISSED} 未获支持。</p>
 */
public record MyReportStatusResponse(
        boolean reported,
        ReportStatus status,
        Instant createdAt,
        Instant handledAt
) {
}
