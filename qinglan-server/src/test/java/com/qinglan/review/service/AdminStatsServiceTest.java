package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminStatsService adminStatsService;

    @Test
    @DisplayName("看板返回三组指标，近 7 日固定 7 项且升序")
    void shouldBuildOverview() {
        when(postRepository.count()).thenReturn(120L);
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(7L);
        when(postRepository.countByStatus(PostStatus.TAKEN_DOWN)).thenReturn(4L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(5L);
        when(appealRepository.countByStatus(AppealStatus.PENDING)).thenReturn(2L);
        when(userRepository.count()).thenReturn(80L);
        when(userRepository.countByStatusIn(anyCollection())).thenReturn(3L);
        when(postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(1L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(2L);
        when(auditLogRepository.countByActionInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyCollection(), any(), any())).thenReturn(6L);

        AdminStatsResponse stats = adminStatsService.overview();

        assertThat(stats.content().totalPosts()).isEqualTo(120L);
        assertThat(stats.content().pendingPosts()).isEqualTo(7L);
        assertThat(stats.content().takenDownPosts()).isEqualTo(4L);
        assertThat(stats.content().todayNewPosts()).isEqualTo(1L);
        assertThat(stats.moderation().pendingReports()).isEqualTo(5L);
        assertThat(stats.moderation().pendingAppeals()).isEqualTo(2L);
        assertThat(stats.moderation().todayHandled()).isEqualTo(6L);
        assertThat(stats.users().totalUsers()).isEqualTo(80L);
        assertThat(stats.users().bannedUsers()).isEqualTo(3L);
        assertThat(stats.users().todayNewUsers()).isEqualTo(2L);

        List<AdminStatsResponse.DailyCount> days = stats.moderation().last7DaysHandled();
        assertThat(days).hasSize(7);
        assertThat(days).extracting(AdminStatsResponse.DailyCount::date).isSorted();
    }

    @Test
    @DisplayName("近 7 日无数据时补 0，仍为 7 项")
    void shouldFillZeroForEmptyDays() {
        when(auditLogRepository.countByActionInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyCollection(), any(), any())).thenReturn(0L);
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(userRepository.countByStatusIn(anyCollection())).thenReturn(0L);

        AdminStatsResponse stats = adminStatsService.overview();

        assertThat(stats.moderation().last7DaysHandled()).hasSize(7);
        assertThat(stats.moderation().last7DaysHandled())
                .allSatisfy(day -> assertThat(day.count()).isZero());
        assertThat(stats.moderation().todayHandled()).isZero();
    }

    @Test
    @DisplayName("处置数只统计治理动作：普通用户浏览联系方式（CONTACT_VIEW）不计入（R-31）")
    void shouldCountOnlyModerationActions() {
        when(auditLogRepository.countByActionInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anyCollection(), any(), any())).thenReturn(0L);
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(userRepository.countByStatusIn(anyCollection())).thenReturn(0L);

        adminStatsService.overview();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<AuditAction>> actions = ArgumentCaptor.forClass(Collection.class);
        verify(auditLogRepository, atLeastOnce())
                .countByActionInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        actions.capture(), any(), any());

        // CONTACT_VIEW 由浏览行为触发、量级远高于真实处置，计入会让「今日处置」失真
        assertThat(actions.getValue())
                .contains(AuditAction.POST_TAKEDOWN, AuditAction.REPORT_DISMISS, AuditAction.BAN)
                .doesNotContain(AuditAction.CONTACT_VIEW, AuditAction.ANNOUNCEMENT_PUBLISH);
    }
}
