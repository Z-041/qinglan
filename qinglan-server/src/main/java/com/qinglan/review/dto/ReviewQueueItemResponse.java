package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;

import java.time.Instant;
import java.util.List;

/**
 * 审核队列的一行（管理端）。
 *
 * <p>刻意不含举报人任何信息：举报人身份不出审核队列（不变量 #1 的引申，
 * 与 {@link CreateReportRequest} 的注释同源）。也不含作者的真实身份——
 * 作者只以匿名代号出现，管理员看不到学号。</p>
 *
 * @param preview         内容摘要，供管理员快速判断，超长已截断
 * @param targetStatus    目标当前状态（帖子的审核状态 / 评论的发布状态），前端按码映射文案
 * @param reportCount     待处理举报数
 * @param firstReportedAt 首条举报时间
 * @param reasons         去重后的举报理由，按首次出现顺序
 */
public record ReviewQueueItemResponse(
        ReviewTargetType targetType,
        Long targetId,
        String preview,
        String targetStatus,
        int reportCount,
        Instant firstReportedAt,
        List<ReportReason> reasons
) {
}
