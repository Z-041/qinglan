package com.qinglan.review;

import com.qinglan.review.dto.AdminStatsResponse;
import com.qinglan.review.service.AdminStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 看板聚合的集成测试（真实 MySQL），核对两件在纯单测里验不到的事：
 *
 * <ul>
 *   <li><b>M6 等价性</b>：一条 GROUP BY 的按日结果，与「同数据逐日 COUNT」的结果逐日相等；</li>
 *   <li><b>跨日边界</b>：日界线是 Asia/Shanghai（SQL 里的 {@code + interval 8 hour} 与 Service 的
 *       {@code STATS_ZONE} 必须同义）。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminStatsAggregationIntegrationTest {

    private static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 与 AdminStatsService.HANDLED_ACTIONS 同集，用于独立地逐日 COUNT 做对照 */
    private static final List<String> HANDLED_ACTIONS = List.of(
            "BAN", "UNBAN", "CREDIT_ADJUST", "POST_APPROVE", "POST_REJECT", "POST_AUTO_TAKEDOWN",
            "POST_TAKEDOWN", "POST_RESTORE", "POST_HIDE_CONTACT", "POST_SHOW_CONTACT", "COMMENT_REMOVE",
            "REPORT_DISMISS", "REPORT_UPHOLD", "APPEAL_REVIEW");

    @Autowired
    private AdminStatsService adminStatsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("M6：GROUP BY 按日结果与逐日 COUNT 一致（同数据对比）")
    void groupedByDayMatchesPerDayCount() {
        LocalDate today = LocalDate.now(STATS_ZONE);
        Instant day1 = today.minusDays(1).atStartOfDay(STATS_ZONE).toInstant().plusSeconds(3600);
        Instant day3 = today.minusDays(3).atStartOfDay(STATS_ZONE).toInstant().plusSeconds(7200);
        insertLog(day1, "BAN");
        insertLog(day1, "POST_TAKEDOWN");
        insertLog(day3, "POST_APPROVE");

        Map<LocalDate, Long> grouped = groupedByDay(adminStatsService.overview().trends().last30Days());

        for (LocalDate day : List.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(3))) {
            long direct = directCount(day);
            assertThat(grouped.getOrDefault(day, 0L))
                    .as("day %s：GROUP BY 结果应与逐日 COUNT 一致", day)
                    .isEqualTo(direct);
        }
    }

    @Test
    @DisplayName("跨日边界：00:00:00 +08 归当天，23:59:59 +08 归前一天")
    void shouldSplitDaysAtShanghaiMidnight() {
        LocalDate today = LocalDate.now(STATS_ZONE);
        Instant justBeforeMidnight = today.atStartOfDay(STATS_ZONE).toInstant().minusSeconds(1);
        Instant midnight = today.atStartOfDay(STATS_ZONE).toInstant();

        Map<LocalDate, Long> before = groupedByDay(adminStatsService.overview().trends().last30Days());
        insertLog(justBeforeMidnight, "BAN");
        insertLog(midnight, "BAN");
        Map<LocalDate, Long> after = groupedByDay(adminStatsService.overview().trends().last30Days());

        // 用差值断言，免受库中既有审计数据影响
        assertThat(after.getOrDefault(today, 0L) - before.getOrDefault(today, 0L)).isEqualTo(1L);
        assertThat(after.getOrDefault(today.minusDays(1), 0L) - before.getOrDefault(today.minusDays(1), 0L))
                .isEqualTo(1L);
    }

    private Map<LocalDate, Long> groupedByDay(List<AdminStatsResponse.DailyCount> days) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (AdminStatsResponse.DailyCount day : days) {
            map.put(day.date(), day.count());
        }
        return map;
    }

    /** 独立地按 Asia/Shanghai 自然日做一次 COUNT，作为 GROUP BY 的对照 */
    private long directCount(LocalDate day) {
        String placeholders = String.join(",", HANDLED_ACTIONS.stream().map(a -> "?").toList());
        String sql = "SELECT COUNT(*) FROM review_log WHERE created_at >= ? AND created_at < ? AND action IN ("
                + placeholders + ")";
        Object[] args = new Object[2 + HANDLED_ACTIONS.size()];
        args[0] = Timestamp.from(day.atStartOfDay(STATS_ZONE).toInstant());
        args[1] = Timestamp.from(day.plusDays(1).atStartOfDay(STATS_ZONE).toInstant());
        for (int i = 0; i < HANDLED_ACTIONS.size(); i++) {
            args[2 + i] = HANDLED_ACTIONS.get(i);
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }

    private void insertLog(Instant createdAt, String action) {
        jdbcTemplate.update(
                "INSERT INTO review_log (target_type, target_id, action, operator_id, reason, created_at) "
                        + "VALUES ('POST', 1, ?, 99, 'seed', ?)",
                action, Timestamp.from(createdAt));
    }
}
