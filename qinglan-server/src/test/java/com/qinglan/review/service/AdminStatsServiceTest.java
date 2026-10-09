package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminStatsResponse;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.repository.AdminStatsRepository;
import com.qinglan.review.repository.AppealRepository;
import com.qinglan.review.repository.ReportHandlingTime;
import com.qinglan.review.repository.ReportReasonCount;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 看板聚合的单元测试（阶段 C）。
 *
 * <p>盯三件事：① 处置趋势来自**一条 GROUP BY**（M6），「今日处置」「近 7 日」「近 30 日」三处
 * 口径同源；② 处置数只统计治理动作，不含查看联系方式 / 公告 / 导出；③ 三项新增指标的口径
 * （分位、通过率）在边界上正确。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    private static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReviewProposalRepository proposalRepository;

    @Mock
    private AdminStatsRepository adminStatsRepository;

    @InjectMocks
    private AdminStatsService adminStatsService;

    @Test
    @DisplayName("看板一次聚合：三组基础指标 + 趋势 7/30 + 理由分布 + 时效 + 通过率 + 排行")
    void shouldBuildOverview() {
        LocalDate today = LocalDate.now(STATS_ZONE);
        when(postRepository.count()).thenReturn(120L);
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(7L);
        when(postRepository.countByStatus(PostStatus.TAKEN_DOWN)).thenReturn(4L);
        when(postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(1L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(5L);
        when(appealRepository.countByStatus(AppealStatus.PENDING)).thenReturn(2L);
        when(userRepository.count()).thenReturn(80L);
        when(userRepository.countByStatusIn(anyCollection())).thenReturn(3L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(2L);
        // 一条 GROUP BY：今日 6 条、昨日 4 条
        when(adminStatsRepository.countHandledByDay(anyCollection(), any(), any())).thenReturn(List.of(
                new Object[]{today.toString(), 6L},
                new Object[]{today.minusDays(1).toString(), 4L}));
        when(reportRepository.countGroupedByReason()).thenReturn(List.of(
                new ReportReasonCount(ReportReason.ABUSE, 3L)));
        when(reportRepository.findHandledTimes(any())).thenReturn(List.of());
        when(proposalRepository.countByStatus(ProposalStatus.APPROVED)).thenReturn(3L);
        when(proposalRepository.countByStatus(ProposalStatus.REJECTED)).thenReturn(1L);
        when(proposalRepository.countByStatus(ProposalStatus.ESCALATED)).thenReturn(2L);
        when(adminStatsRepository.countHandledByOperator(anyCollection(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{99L, 5L}));

        AdminStatsResponse stats = adminStatsService.overview();

        assertThat(stats.content().totalPosts()).isEqualTo(120L);
        assertThat(stats.content().pendingPosts()).isEqualTo(7L);
        assertThat(stats.content().todayNewPosts()).isEqualTo(1L);
        assertThat(stats.moderation().pendingReports()).isEqualTo(5L);
        assertThat(stats.moderation().pendingAppeals()).isEqualTo(2L);
        // 今日处置来自 GROUP BY 的当日桶
        assertThat(stats.moderation().todayHandled()).isEqualTo(6L);
        assertThat(stats.users().totalUsers()).isEqualTo(80L);

        // 近 7 日 / 近 30 日均为补零后的固定长度、升序
        assertThat(stats.trends().last7Days()).hasSize(7);
        assertThat(stats.trends().last30Days()).hasSize(30);
        assertThat(stats.trends().last7Days())
                .extracting(AdminStatsResponse.DailyCount::date).isSorted();
        assertThat(stats.trends().last30Days().get(29).count()).isEqualTo(6L);
        // 「近 7 日」与 moderation.last7DaysHandled 同源
        assertThat(stats.moderation().last7DaysHandled()).isEqualTo(stats.trends().last7Days());

        assertThat(stats.reportReasons()).containsExactly(
                new AdminStatsResponse.ReasonCount(ReportReason.ABUSE, 3L));
        assertThat(stats.proposals().approved()).isEqualTo(3L);
        assertThat(stats.proposals().rejected()).isEqualTo(1L);
        assertThat(stats.proposals().escalated()).isEqualTo(2L);
        assertThat(stats.proposals().approvalRate()).isEqualTo(0.75);
        assertThat(stats.moderatorRanking()).containsExactly(
                new AdminStatsResponse.ModeratorRank(99L, 5L));
    }

    @Test
    @DisplayName("近 7 日 / 近 30 日无数据时补 0，仍为固定项数")
    void shouldFillZeroForEmptyDays() {
        when(adminStatsRepository.countHandledByDay(anyCollection(), any(), any())).thenReturn(List.of());
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(postRepository.countByStatus(PostStatus.TAKEN_DOWN)).thenReturn(0L);
        when(userRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(reportRepository.countGroupedByReason()).thenReturn(List.of());
        when(reportRepository.findHandledTimes(any())).thenReturn(List.of());
        when(adminStatsRepository.countHandledByOperator(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());

        AdminStatsResponse stats = adminStatsService.overview();

        assertThat(stats.trends().last7Days()).hasSize(7);
        assertThat(stats.trends().last7Days()).allSatisfy(day -> assertThat(day.count()).isZero());
        assertThat(stats.trends().last30Days()).hasSize(30);
        assertThat(stats.moderation().todayHandled()).isZero();
    }

    @Test
    @DisplayName("处置数只统计治理动作：查看联系方式 / 公告 / 导出都不计入（原生查询同理）")
    void shouldCountOnlyModerationActions() {
        when(adminStatsRepository.countHandledByDay(anyCollection(), any(), any())).thenReturn(List.of());
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(postRepository.countByStatus(PostStatus.TAKEN_DOWN)).thenReturn(0L);
        when(userRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(reportRepository.countGroupedByReason()).thenReturn(List.of());
        when(reportRepository.findHandledTimes(any())).thenReturn(List.of());
        when(adminStatsRepository.countHandledByOperator(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());

        adminStatsService.overview();

        // 原生按日查询收到的是动作名集合
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> dayActions = ArgumentCaptor.forClass(Collection.class);
        verify(adminStatsRepository, atLeastOnce())
                .countHandledByDay(dayActions.capture(), any(), any());
        assertThat(dayActions.getValue())
                .contains(AuditAction.POST_TAKEDOWN.name(), AuditAction.REPORT_DISMISS.name(), AuditAction.BAN.name())
                .doesNotContain(AuditAction.CONTACT_VIEW.name(), AuditAction.ANNOUNCEMENT_PUBLISH.name(),
                        AuditAction.DATA_EXPORT.name());

        // 排行查询收到的是动作枚举集合
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<AuditAction>> rankActions = ArgumentCaptor.forClass(Collection.class);
        verify(adminStatsRepository).countHandledByOperator(rankActions.capture(), any(Pageable.class));
        assertThat(rankActions.getValue()).doesNotContain(AuditAction.CONTACT_VIEW, AuditAction.DATA_EXPORT);
    }

    @Test
    @DisplayName("处置时效按最近秩法取 P50 / P90；无样本时为 0")
    void shouldComputeLatencyPercentiles() {
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        when(reportRepository.findHandledTimes(any())).thenReturn(List.of(
                time(base, 10), time(base, 20), time(base, 30), time(base, 40)));
        stubEmptyRest();

        AdminStatsResponse stats = adminStatsService.overview();

        assertThat(stats.dispositionLatency().sampleSize()).isEqualTo(4);
        // 最近秩法：P50 → 第 2 个（20s），P90 → 第 4 个（40s）
        assertThat(stats.dispositionLatency().p50Seconds()).isEqualTo(20L);
        assertThat(stats.dispositionLatency().p90Seconds()).isEqualTo(40L);
    }

    @Test
    @DisplayName("提案通过率：无已结案提案时为 0，不产生 NaN")
    void shouldGuardProposalRateWhenNoResolved() {
        when(proposalRepository.countByStatus(ProposalStatus.APPROVED)).thenReturn(0L);
        when(proposalRepository.countByStatus(ProposalStatus.REJECTED)).thenReturn(0L);
        when(proposalRepository.countByStatus(ProposalStatus.ESCALATED)).thenReturn(2L);
        stubEmptyRest();

        AdminStatsResponse stats = adminStatsService.overview();

        assertThat(stats.proposals().approvalRate()).isZero();
    }

    @Test
    @DisplayName("不变量 #3：审核员排行只回 operatorId 与计数，不含任何身份字段")
    void shouldNotExposeIdentityInRanking() {
        Set<String> names = Arrays.stream(AdminStatsResponse.ModeratorRank.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder("operatorId", "count");
    }

    private void stubEmptyRest() {
        when(adminStatsRepository.countHandledByDay(anyCollection(), any(), any())).thenReturn(List.of());
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(postRepository.countByStatus(PostStatus.TAKEN_DOWN)).thenReturn(0L);
        when(userRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(reportRepository.countGroupedByReason()).thenReturn(List.of());
        when(adminStatsRepository.countHandledByOperator(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private static ReportHandlingTime time(Instant reportedAt, long handledAfterSeconds) {
        return new ReportHandlingTime(reportedAt, reportedAt.plusSeconds(handledAfterSeconds));
    }
}
