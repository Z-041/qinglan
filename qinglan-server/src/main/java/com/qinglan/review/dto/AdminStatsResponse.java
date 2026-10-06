package com.qinglan.review.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据看板。
 *
 * <p>跨模块聚合放 {@code review}：它是白名单里唯一能同时读 {@code post} / {@code comment} /
 * {@code user} 的模块（AGENTS.md 第三章），零白名单改动。</p>
 *
 * <p>日界线统一按 Asia/Shanghai 切分：按 UTC 切会让管理员看到的「今日」与直觉差 8 小时。</p>
 */
public record AdminStatsResponse(
        ContentStats content,
        ModerationStats moderation,
        UserStats users
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

    /** 单日处置数 */
    public record DailyCount(LocalDate date, long count) {
    }
}
