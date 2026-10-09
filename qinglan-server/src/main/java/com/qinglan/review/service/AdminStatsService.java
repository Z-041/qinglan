package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminStatsResponse;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.repository.AdminStatsRepository;
import com.qinglan.review.repository.AppealRepository;
import com.qinglan.review.repository.ReportHandlingTime;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营看板（阶段 C）。
 *
 * <p>所有计数聚合一次算出。处置趋势由**一条 GROUP BY**（{@link AdminStatsRepository#countHandledByDay}）
 * 覆盖，替代原先把今日 + 近 7 日拆成 8 次 COUNT 的写法（消化后端缺陷 M6）；「今日处置」与「近 7 日」
 * 两组数都从同一份分组结果里取，口径天然一致。</p>
 *
 * <p>日界线固定 Asia/Shanghai。时区换算在 SQL 侧以 {@code + interval 8 hour} 完成（唯一一处，
 * 见 {@link AdminStatsRepository}），与本地 {@code STATS_ZONE} 同义。</p>
 *
 * <p><b>方案未定义口径的三项指标，此处选定如下（均写明为假设，供产品确认后调整）：</b></p>
 * <ul>
 *   <li><b>处置时效 P50 / P90</b>：样本 = 近 30 天内**已处置**的举报，耗时 = {@code handled_at − created_at}
 *       （秒），最近秩法取分位。定义为「举报从提交到处置」而非「内容发布到处置」，因为前者用举报表两列
 *       即可算、口径清晰，后者要跨越 post/comment 与审计表多次关联。</li>
 *   <li><b>提案通过率</b>：{@code approved / (approved + rejected)}，全时段。ESCALATED（僵局待裁决）
 *       尚未结案，不计入分母。</li>
 *   <li><b>举报按理由分布</b>：全时段、不限举报状态的计数（按理由分组，行数受枚举基数约束）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    /** 日界线时区：与管理员的直觉一致，不用 UTC */
    private static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");
    /** 处置趋势长窗口天数（近 30 日）；短窗口固定 7 日 */
    private static final int TREND_DAYS = 30;
    private static final int RECENT_DAYS = 7;
    /** 处置时效统计窗口（天） */
    private static final int LATENCY_WINDOW_DAYS = 30;
    /** 审核员处置量排行取前多少名 */
    private static final int RANKING_LIMIT = 10;

    /**
     * 计入「处置数」的动作集合。看板这一列的语义是**治理动作次数**，故：
     * 排除 {@code CONTACT_VIEW}（由普通用户浏览触发，量级远高于真实处置）；
     * 排除 {@code ANNOUNCEMENT_*}（发布公告不是内容处置，另有公告管理页）；
     * 排除 {@code DATA_EXPORT}（导出是批量读取而非处置）。
     */
    private static final List<AuditAction> HANDLED_ACTIONS = List.of(
            AuditAction.BAN,
            AuditAction.UNBAN,
            AuditAction.CREDIT_ADJUST,
            AuditAction.POST_APPROVE,
            AuditAction.POST_REJECT,
            AuditAction.POST_AUTO_TAKEDOWN,
            AuditAction.POST_TAKEDOWN,
            AuditAction.POST_RESTORE,
            AuditAction.POST_HIDE_CONTACT,
            AuditAction.POST_SHOW_CONTACT,
            AuditAction.COMMENT_REMOVE,
            AuditAction.REPORT_DISMISS,
            AuditAction.REPORT_UPHOLD,
            AuditAction.APPEAL_REVIEW);

    /** 原生查询需要动作名（枚举名）集合 */
    private static final List<String> HANDLED_ACTION_NAMES =
            HANDLED_ACTIONS.stream().map(Enum::name).toList();

    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final ReviewProposalRepository proposalRepository;
    private final AdminStatsRepository adminStatsRepository;

    @Transactional(readOnly = true)
    public AdminStatsResponse overview() {
        LocalDate today = LocalDate.now(STATS_ZONE);
        Instant todayStart = startOfDay(today);
        Instant tomorrowStart = startOfDay(today.plusDays(1));

        // 一条 GROUP BY 覆盖「今日 + 近 7 日 + 近 30 日」的全部处置数（M6）
        Map<LocalDate, Long> handledByDay =
                handledByDay(startOfDay(today.minusDays(TREND_DAYS - 1)), tomorrowStart);

        AdminStatsResponse.ContentStats content = new AdminStatsResponse.ContentStats(
                postRepository.count(),
                postRepository.countByStatusIn(List.of(PostStatus.PENDING, PostStatus.REVIEWING)),
                postRepository.countByStatus(PostStatus.TAKEN_DOWN),
                postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart));

        AdminStatsResponse.ModerationStats moderation = new AdminStatsResponse.ModerationStats(
                reportRepository.countByStatus(ReportStatus.PENDING),
                appealRepository.countByStatus(AppealStatus.PENDING),
                handledByDay.getOrDefault(today, 0L),
                trend(handledByDay, today, RECENT_DAYS));

        AdminStatsResponse.UserStats users = new AdminStatsResponse.UserStats(
                userRepository.count(),
                userRepository.countByStatusIn(List.of(UserStatus.BANNED, UserStatus.BLACKLISTED)),
                userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart));

        AdminStatsResponse.TrendStats trends = new AdminStatsResponse.TrendStats(
                trend(handledByDay, today, RECENT_DAYS),
                trend(handledByDay, today, TREND_DAYS));

        return new AdminStatsResponse(
                content,
                moderation,
                users,
                trends,
                reasonDistribution(),
                dispositionLatency(today),
                proposals(),
                moderatorRanking());
    }

    /** 把「按日分组」的原始结果补齐成固定天数的升序序列，无数据的日期补 0 */
    private List<AdminStatsResponse.DailyCount> trend(Map<LocalDate, Long> handledByDay, LocalDate today, int days) {
        List<AdminStatsResponse.DailyCount> result = new ArrayList<>(days);
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            result.add(new AdminStatsResponse.DailyCount(day, handledByDay.getOrDefault(day, 0L)));
        }
        return List.copyOf(result);
    }

    private Map<LocalDate, Long> handledByDay(Instant from, Instant to) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : adminStatsRepository.countHandledByDay(HANDLED_ACTION_NAMES, from, to)) {
            // date_format 返回字符串，用 LocalDate.parse 解析，避免 DATE 列的驱动差异
            counts.put(LocalDate.parse((String) row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private List<AdminStatsResponse.ReasonCount> reasonDistribution() {
        return reportRepository.countGroupedByReason().stream()
                .map(row -> new AdminStatsResponse.ReasonCount(row.reason(), row.count()))
                .toList();
    }

    /**
     * 处置时效 P50 / P90：近 {@value #LATENCY_WINDOW_DAYS} 天已处置举报的
     * {@code handled_at − created_at}（秒），最近秩法取分位。
     */
    private AdminStatsResponse.LatencyStats dispositionLatency(LocalDate today) {
        Instant from = startOfDay(today.minusDays(LATENCY_WINDOW_DAYS - 1L));
        List<Long> durations = reportRepository.findHandledTimes(from).stream()
                .map(AdminStatsService::secondsBetween)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new AdminStatsResponse.LatencyStats(
                percentile(durations, 50), percentile(durations, 90), durations.size());
    }

    private static long secondsBetween(ReportHandlingTime time) {
        long seconds = Duration.between(time.reportedAt(), time.handledAt()).getSeconds();
        // 极端脏数据（处置时刻早于提交时刻）按 0 处理，避免负耗时光进分位
        return Math.max(0L, seconds);
    }

    /** 最近秩法：升序序列中取 ceil(p/100 × n) 处的值（1 基），n=0 时返回 0 */
    private static long percentile(List<Long> sortedAscending, int p) {
        int size = sortedAscending.size();
        if (size == 0) {
            return 0L;
        }
        int rank = (int) Math.ceil(p / 100.0 * size);
        int index = Math.min(size, Math.max(1, rank)) - 1;
        return sortedAscending.get(index);
    }

    private AdminStatsResponse.ProposalStats proposals() {
        long approved = proposalRepository.countByStatus(ProposalStatus.APPROVED);
        long rejected = proposalRepository.countByStatus(ProposalStatus.REJECTED);
        long escalated = proposalRepository.countByStatus(ProposalStatus.ESCALATED);
        long resolved = approved + rejected;
        double approvalRate = resolved == 0 ? 0.0 : (double) approved / resolved;
        return new AdminStatsResponse.ProposalStats(approved, rejected, escalated, approvalRate);
    }

    private List<AdminStatsResponse.ModeratorRank> moderatorRanking() {
        return adminStatsRepository
                .countHandledByOperator(HANDLED_ACTIONS, PageRequest.of(0, RANKING_LIMIT)).stream()
                .map(row -> new AdminStatsResponse.ModeratorRank(
                        ((Number) row[0]).longValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    private Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(STATS_ZONE).toInstant();
    }
}
