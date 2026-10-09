package com.qinglan.review.dto;

import com.qinglan.review.entity.ReportReason;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据看板（阶段 C）。
 *
 * <p>跨模块聚合放 {@code review}：它是白名单里唯一能同时读 {@code post} / {@code comment} /
 * {@code user} 的模块（AGENTS.md 第三章），零白名单改动。</p>
 *
 * <p>日界线统一按 Asia/Shanghai 切分：按 UTC 切会让管理员看到的「今日」与直觉差 8 小时。
 * 处置趋势由一条 GROUP BY 算出（M6），不再逐日 COUNT。</p>
 *
 * <p>三项新增指标的口径（方案未定义，此处为**选定口径**，见 {@code AdminStatsService} 注释）：
 * 处置时效以「举报提交 → 处置完成」的近 30 天样本计；提案通过率以「准予 / (准予 + 不予)」计。</p>
 */
public record AdminStatsResponse(
        ContentStats content,
        ModerationStats moderation,
        UserStats users,
        TrendStats trends,
        List<ReasonCount> reportReasons,
        LatencyStats dispositionLatency,
        ProposalStats proposals,
        List<ModeratorRank> moderatorRanking
) {

    /** {@code todayNewPosts} 等「今日」均按 Asia/Shanghai 的自然日 */
    public record ContentStats(
            long totalPosts,
            long pendingPosts,
            long takenDownPosts,
            long todayNewPosts
    ) {
    }

    /** {@code last7DaysHandled} 固定 7 项、按日期升序，无数据的日期补 0 */
    public record ModerationStats(
            long pendingReports,
            long pendingAppeals,
            long todayHandled,
            List<DailyCount> last7DaysHandled
    ) {
    }

    /** {@code bannedUsers} 含 BANNED 与 BLACKLISTED 两态 */
    public record UserStats(
            long totalUsers,
            long bannedUsers,
            long todayNewUsers
    ) {
    }

    /** 处置趋势：近 7 日与近 30 日两组，均按日期升序、无数据补 0 */
    public record TrendStats(
            List<DailyCount> last7Days,
            List<DailyCount> last30Days
    ) {
    }

    /** 单日处置数 */
    public record DailyCount(LocalDate date, long count) {
    }

    /** 举报按理由分布的一项（举报总数，不限状态） */
    public record ReasonCount(ReportReason reason, long count) {
    }

    /**
     * 处置时效分位数（最近秩法）。
     *
     * @param p50Seconds 中位数耗时（秒）
     * @param p90Seconds 90 分位耗时（秒）
     * @param sampleSize 参与统计的样本数；为 0 时两个分位数均为 0
     */
    public record LatencyStats(long p50Seconds, long p90Seconds, long sampleSize) {
    }

    /**
     * 提案表决概况。
     *
     * @param approvalRate 通过率 = approved / (approved + rejected)；分母为 0 时为 0。
     *                     ESCALATED（僵局待裁决）尚未结案，不计入分母
     */
    public record ProposalStats(long approved, long rejected, long escalated, double approvalRate) {
    }

    /** 审核员处置量排行的一项：只有 operatorId 与计数，不含任何身份明文 */
    public record ModeratorRank(long operatorId, long count) {
    }
}
