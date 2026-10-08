package com.qinglan.review.dto;

import java.time.Instant;
import java.util.List;

/**
 * 待审帖子队列的一行（管理端）。
 *
 * <p>先审后发（设计方案第一章红线 2）下新帖一律落在 {@code PENDING}，要有人放行才可见。
 * 而举报队列是按「被举报目标」聚合的，没人举报的新帖永远不会出现——本队列补的就是这个缺口。</p>
 *
 * <p>刻意不含 {@code authorId} / {@code anonymousCode}：作者身份不出队列（不变量 #1 的引申，
 * 与 {@link ReviewQueueItemResponse} 同口径）。管理端只看到「内容 + 状态 + 等待时长」，
 * 需要更多线索时按 {@code postId} 打开上下文面板。</p>
 *
 * @param postId       处置接口（approve / reject）与上下文面板所需的目标 id
 * @param preview      帖子内容摘要，超长已截断，与其余两个队列同口径
 * @param images       该帖全部图片 URL，按 {@code post_image.sort_order} 升序保序；
 *                     没有图片时是空列表而非 {@code null}（前端无需判空）。
 *                     <b>没有它，管理员放行时看不到图——图片便随帖直接放行展示，等于漏审</b>；
 *                     故必须在这里返回全部（单帖上限由 {@code qinglan.post.max-images-per-post} 约束，无界风险不存在）
 * @param targetStatus 帖子当前状态码，前端按码映射文案
 * @param submittedAt  提交审核的时间（即 {@code post.created_at}——发布与提交在同一事务、同一时刻），
 *                     界面据它展示等待时长，排序也用它
 * @param reportCount  待处理举报数：新帖通常为 0，非 0 说明该帖同时被举报，是排队时的重要线索
 */
public record PendingPostItemResponse(
        Long postId,
        String preview,
        List<String> images,
        String targetStatus,
        Instant submittedAt,
        int reportCount
) {
}
