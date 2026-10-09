package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;

import java.util.List;

/**
 * 审核队列的筛选与排序条件（管理端阶段 A）。
 *
 * <p>筛选一律进 SQL（{@link com.qinglan.review.repository.ReportRepository}），而非前端本地过滤——
 * 否则游标分页的口径会被打破：前端只拿到当前一页，本地过滤后翻页结果不可预期。</p>
 *
 * @param reasons     多选举报理由；为空表示不按理由过滤
 * @param targetType  目标类型；为 {@code null} 表示不过滤
 * @param overdueOnly 只看超时（首报早于 now-24h）；与前端标红口径一致
 * @param sort        排序口径；为 {@code null} 时按默认（{@link ReviewQueueSort#REPORT_COUNT_DESC}）
 */
public record ReviewQueueQuery(
        List<ReportReason> reasons,
        ReviewTargetType targetType,
        boolean overdueOnly,
        ReviewQueueSort sort
) {

    /** 归一化：null 理由集合转空列表、null 排序转默认，调用方无需各自判空 */
    public ReviewQueueQuery {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        sort = sort == null ? ReviewQueueSort.REPORT_COUNT_DESC : sort;
    }
}
