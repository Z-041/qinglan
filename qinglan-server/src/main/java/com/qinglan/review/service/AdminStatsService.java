package com.qinglan.review.service;

import com.qinglan.common.audit.AuditLogRepository;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminStatsResponse;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.repository.AppealRepository;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 运营看板。
 *
 * <p>全部为计数聚合，逐条走带条件的 count 查询；「近 7 日」按 Asia/Shanghai 的自然日边界
 * 逐日查 7 次，无数据的日期补 0，保证前端永远拿到固定 7 项。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    /** 日界线时区：与管理员的直觉一致，不用 UTC */
    private static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int RECENT_DAYS = 7;

    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public AdminStatsResponse overview() {
        LocalDate today = LocalDate.now(STATS_ZONE);
        Instant todayStart = startOfDay(today);
        Instant tomorrowStart = startOfDay(today.plusDays(1));

        AdminStatsResponse.ContentStats content = new AdminStatsResponse.ContentStats(
                postRepository.count(),
                postRepository.countByStatusIn(List.of(PostStatus.PENDING, PostStatus.REVIEWING)),
                postRepository.countByStatus(PostStatus.TAKEN_DOWN),
                postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart));

        AdminStatsResponse.ModerationStats moderation = new AdminStatsResponse.ModerationStats(
                reportRepository.countByStatus(ReportStatus.PENDING),
                appealRepository.countByStatus(AppealStatus.PENDING),
                auditLogRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart),
                recentDaysHandled(today));

        AdminStatsResponse.UserStats users = new AdminStatsResponse.UserStats(
                userRepository.count(),
                userRepository.countByStatusIn(List.of(UserStatus.BANNED, UserStatus.BLACKLISTED)),
                userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart));

        return new AdminStatsResponse(content, moderation, users);
    }

    private List<AdminStatsResponse.DailyCount> recentDaysHandled(LocalDate today) {
        List<AdminStatsResponse.DailyCount> days = new ArrayList<>(RECENT_DAYS);
        for (int offset = RECENT_DAYS - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            long count = auditLogRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    startOfDay(day), startOfDay(day.plusDays(1)));
            days.add(new AdminStatsResponse.DailyCount(day, count));
        }
        return List.copyOf(days);
    }

    private Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(STATS_ZONE).toInstant();
    }
}
