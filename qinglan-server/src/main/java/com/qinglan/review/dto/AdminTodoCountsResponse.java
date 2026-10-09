package com.qinglan.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 待办计数（阶段 B）：顶栏铃铛轮询的响应。
 *
 * <p><b>无权限的分组不返回，而不是返回 0</b>——返回 0 会让前端画出「0 条待办」的假象，
 * 反而比不显示更糟。用 {@link JsonInclude.Include#NON_NULL} 让无权限的字段整个缺席。</p>
 *
 * @param pendingPosts   待审帖子数（PENDING + REVIEWING，与看板口径一致）；无 MODERATION_VIEW 时为 null
 * @param pendingReports 待处理举报的**目标数**（与审核队列行数一致，非举报条数）；无 MODERATION_VIEW 时为 null
 * @param proposable     可投票的进行中提案数（OPEN）；无 PROPOSAL_VOTE 时为 null
 * @param escalated      僵局待裁决数（ESCALATED）；无 PROPOSAL_ARBITRATE 时为 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminTodoCountsResponse(
        Long pendingPosts,
        Long pendingReports,
        Long proposable,
        Long escalated
) {
}
