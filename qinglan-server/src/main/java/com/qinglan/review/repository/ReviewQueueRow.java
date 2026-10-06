package com.qinglan.review.repository;

import com.qinglan.review.entity.ReviewTargetType;

import java.time.Instant;

/**
 * 审核队列的聚合行：一个举报目标一行。
 *
 * <p>聚合在数据库里做（按目标 group by），避免把全部待处理举报捞进内存再分组。
 * 本类型只在 review 模块内部流转，不直接对外序列化。</p>
 *
 * @param reportCount     该目标的待处理举报数，队列首要排序键
 * @param firstReportedAt 首条举报时间，即「等待时长」的起点，并列时先报的靠前
 */
public record ReviewQueueRow(
        ReviewTargetType targetType,
        Long targetId,
        long reportCount,
        Instant firstReportedAt
) {
}
