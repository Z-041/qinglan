package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;

import java.util.List;

/**
 * 审核工作台的上下文面板（设计书第 138 行）。
 *
 * <p>刻意不含作者 id 与举报人 id：作者只以信用分与违规次数出现，举报人只以聚合人数出现
 * （不变量 #1 的引申）。前端拿不到任何可用于关联具体人的字段。</p>
 *
 * @param authorViolationCount   该作者被处置过的累计次数（含已恢复的历史）
 * @param reportCount            待处理举报数
 * @param distinctReporterCount  该目标的历史举报人数（去重）
 * @param repeatedReporterCount  其中「还举报过其他目标」的人数，即重复举报人
 */
public record AdminReviewContextResponse(
        ReviewTargetType targetType,
        Long targetId,
        String targetStatus,
        int authorCreditScore,
        long authorViolationCount,
        int reportCount,
        int distinctReporterCount,
        long repeatedReporterCount,
        List<ReportReason> reasons
) {
}
