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
 * @param images          帖子的全部图片 URL，按 {@code post_image.sort_order} 升序保序；
 *                        目标类型是 {@code COMMENT}（评论无图）或帖子无图时是空列表而非 {@code null}（前端无需判空）。
 *                        <b>没有它，管理员只能看到正文摘要——图片会随帖被放行，漏看一张就是漏审</b>
 * @param targetStatus    目标当前状态（帖子的审核状态 / 评论的发布状态），前端按码映射文案
 * @param reportCount     待处理举报数
 * @param firstReportedAt 首条举报时间
 * @param reasons         去重后的举报理由，按首次出现顺序
 * @param details         去重后的举报补充说明（非空项）：举报理由选「其他」时说明即唯一线索，
 *                        此前只入库不出参，管理员在任何界面都读不到
 * @param proposal        该目标上正在进行的处置提案；没有进行中提案时为 {@code null}。
 *                        有它才能在行内显示「已在表决中 2/4 / 我已投」，而不是让人撞 409（方案 9.2）
 */
public record ReviewQueueItemResponse(
        ReviewTargetType targetType,
        Long targetId,
        String preview,
        List<String> images,
        String targetStatus,
        int reportCount,
        Instant firstReportedAt,
        List<ReportReason> reasons,
        List<String> details,
        ReviewQueueProposalResponse proposal
) {
}
