package com.qinglan.announcement.service;

import com.qinglan.announcement.dto.CreateAnnouncementRequest;
import com.qinglan.announcement.dto.MarkReadRequest;
import com.qinglan.announcement.dto.PendingAnnouncementResponse;
import com.qinglan.announcement.entity.Announcement;
import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.announcement.entity.AnnouncementRead;
import com.qinglan.announcement.entity.AnnouncementStatus;
import com.qinglan.announcement.repository.AnnouncementReadRepository;
import com.qinglan.announcement.repository.AnnouncementRepository;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 公告服务的单元测试，重点是不变量 #6（强制阅读真实计时）与 #7（发布/撤回/建草稿留痕）。
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    private static final long ANNOUNCEMENT_ID = 100L;
    private static final long USER_ID = 7L;
    private static final long OPERATOR_ID = 99L;
    private static final Instant BASE = Instant.parse("2026-10-07T00:00:00Z");

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private AnnouncementReadRepository announcementReadRepository;

    @Mock
    private AuditLogService auditLogService;

    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(announcementRepository, announcementReadRepository,
                auditLogService, new AnnouncementProperties(5, 5));
    }

    // ---------------------------------------------------------- 不变量 #6

    @Test
    @DisplayName("不变量 #6-a：停留时长不足时拒绝标已读，且不落库")
    void shouldRejectMarkReadWhenDurationTooShort() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(published(ANNOUNCEMENT_ID, 5)));

        // 5 秒要求，只停了 4 秒
        assertThatThrownBy(() -> service.markRead(
                ANNOUNCEMENT_ID, USER_ID, null, new MarkReadRequest(null, 4_000L, true, true)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_READ_TOO_EARLY);

        // 「打开即已读」的路径不存在：时长不足时一条记录都不写
        verify(announcementReadRepository, never()).save(any(AnnouncementRead.class));
    }

    @Test
    @DisplayName("不变量 #6-b：时长足够时落库，且 readDurationMs 如实记录")
    void shouldPersistReadWithActualDuration() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(published(ANNOUNCEMENT_ID, 5)));
        when(announcementReadRepository.findByAnnouncementIdAndUserId(ANNOUNCEMENT_ID, USER_ID))
                .thenReturn(Optional.empty());

        service.markRead(ANNOUNCEMENT_ID, USER_ID, null, new MarkReadRequest(null, 6_500L, true, true));

        ArgumentCaptor<AnnouncementRead> captor = ArgumentCaptor.forClass(AnnouncementRead.class);
        verify(announcementReadRepository).save(captor.capture());
        AnnouncementRead saved = captor.getValue();
        assertThat(saved.getAnnouncementId()).isEqualTo(ANNOUNCEMENT_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getDeviceId()).isNull();
        assertThat(saved.getReadDurationMs()).isEqualTo(6_500L);
        assertThat(saved.isConfirmed()).isTrue();
        assertThat(saved.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("时长足够但公告不存在时返回 404，不落库")
    void shouldRejectWhenAnnouncementMissing() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(
                ANNOUNCEMENT_ID, USER_ID, null, new MarkReadRequest(null, 6_000L, false, false)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND);

        verifyNoInteractions(announcementReadRepository);
    }

    @Test
    @DisplayName("未登录且 deviceId 为空时返回 400，不落库")
    void shouldRejectWhenNoIdentity() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(published(ANNOUNCEMENT_ID, 5)));

        assertThatThrownBy(() -> service.markRead(
                ANNOUNCEMENT_ID, null, null, new MarkReadRequest(null, 6_000L, false, false)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(announcementReadRepository, never()).save(any(AnnouncementRead.class));
    }

    @Test
    @DisplayName("游客按 deviceId 记录已读，userId 留空")
    void shouldPersistGuestReadByDevice() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(published(ANNOUNCEMENT_ID, 5)));
        when(announcementReadRepository.findByAnnouncementIdAndDeviceId(ANNOUNCEMENT_ID, "dev-1"))
                .thenReturn(Optional.empty());

        service.markRead(ANNOUNCEMENT_ID, null, "dev-1", new MarkReadRequest(null, 6_000L, true, false));

        ArgumentCaptor<AnnouncementRead> captor = ArgumentCaptor.forClass(AnnouncementRead.class);
        verify(announcementReadRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getDeviceId()).isEqualTo("dev-1");
    }

    // ---------------------------------------------------------- 发布状态机

    @Test
    @DisplayName("DRAFT 可发布：置 PUBLISHED 并记录发布时间")
    void shouldPublishDraft() {
        Announcement draft = draft(ANNOUNCEMENT_ID, 5);
        when(announcementRepository.findById(ANNOUNCEMENT_ID)).thenReturn(Optional.of(draft));
        when(announcementRepository.countByStatusAndPublishedAtGreaterThanEqual(
                eq(AnnouncementStatus.PUBLISHED), any(Instant.class))).thenReturn(0L);

        service.publish(OPERATOR_ID, ANNOUNCEMENT_ID);

        assertThat(draft.getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(draft.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("已发布公告不可再发布：状态非法")
    void shouldRejectPublishingPublishedAnnouncement() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(published(ANNOUNCEMENT_ID, 5)));

        assertThatThrownBy(() -> service.publish(OPERATOR_ID, ANNOUNCEMENT_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_STATE_ILLEGAL);
    }

    @Test
    @DisplayName("已撤回公告不可再发布：状态非法（更正走 supersedes_id 新建）")
    void shouldRejectPublishingRevokedAnnouncement() {
        Announcement revoked = published(ANNOUNCEMENT_ID, 5);
        revoked.revoke(BASE);
        when(announcementRepository.findById(ANNOUNCEMENT_ID)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.publish(OPERATOR_ID, ANNOUNCEMENT_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_STATE_ILLEGAL);
    }

    @Test
    @DisplayName("当日发布数达到上限时拒绝发布")
    void shouldRejectWhenDailyPublishLimitReached() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(draft(ANNOUNCEMENT_ID, 5)));
        when(announcementRepository.countByStatusAndPublishedAtGreaterThanEqual(
                eq(AnnouncementStatus.PUBLISHED), any(Instant.class))).thenReturn(5L);

        assertThatThrownBy(() -> service.publish(OPERATOR_ID, ANNOUNCEMENT_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_RATE_LIMITED);
    }

    // ---------------------------------------------------------- 不变量 #7

    @Test
    @DisplayName("不变量 #7：建草稿写入 ANNOUNCEMENT_CREATE 审计")
    void shouldAuditOnCreateDraft() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> {
            Announcement saved = invocation.getArgument(0);
            TestFixtures.setId(saved, ANNOUNCEMENT_ID);
            return saved;
        });

        Long id = service.createDraft(OPERATOR_ID, new CreateAnnouncementRequest(
                "紧急公告", "正文", AnnouncementLevel.URGENT, BASE, null, null, false, false, null));

        assertThat(id).isEqualTo(ANNOUNCEMENT_ID);
        verify(auditLogService).record(AuditTargetType.ANNOUNCEMENT, ANNOUNCEMENT_ID,
                AuditAction.ANNOUNCEMENT_CREATE, OPERATOR_ID, null);
    }

    @Test
    @DisplayName("不变量 #7：发布写入 ANNOUNCEMENT_PUBLISH 审计")
    void shouldAuditOnPublish() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(draft(ANNOUNCEMENT_ID, 5)));
        when(announcementRepository.countByStatusAndPublishedAtGreaterThanEqual(
                eq(AnnouncementStatus.PUBLISHED), any(Instant.class))).thenReturn(0L);

        service.publish(OPERATOR_ID, ANNOUNCEMENT_ID);

        verify(auditLogService).record(AuditTargetType.ANNOUNCEMENT, ANNOUNCEMENT_ID,
                AuditAction.ANNOUNCEMENT_PUBLISH, OPERATOR_ID, null);
    }

    @Test
    @DisplayName("不变量 #7：撤回写入 ANNOUNCEMENT_REVOKE 审计")
    void shouldAuditOnRevoke() {
        when(announcementRepository.findById(ANNOUNCEMENT_ID))
                .thenReturn(Optional.of(published(ANNOUNCEMENT_ID, 5)));

        service.revoke(OPERATOR_ID, ANNOUNCEMENT_ID);

        verify(auditLogService).record(AuditTargetType.ANNOUNCEMENT, ANNOUNCEMENT_ID,
                AuditAction.ANNOUNCEMENT_REVOKE, OPERATOR_ID, null);
    }

    // ---------------------------------------------------------- 待读

    @Test
    @DisplayName("待读取优先级最高的一条；无未读返回空")
    void shouldReturnHighestPriorityPending() {
        when(announcementRepository.findPending(
                eq(AnnouncementStatus.PUBLISHED), eq(AnnouncementLevel.URGENT), eq(AnnouncementLevel.NORMAL),
                any(Instant.class), eq(USER_ID), isNull(), any(Pageable.class)))
                .thenReturn(List.of(published(ANNOUNCEMENT_ID, 5)));

        Optional<PendingAnnouncementResponse> pending = service.findPending(USER_ID, null);

        assertThat(pending).isPresent();
        assertThat(pending.get().id()).isEqualTo(ANNOUNCEMENT_ID);
        assertThat(pending.get().minReadSeconds()).isEqualTo(5);
    }

    @Test
    @DisplayName("无未读公告时待读为空")
    void shouldReturnEmptyWhenNothingPending() {
        when(announcementRepository.findPending(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.findPending(USER_ID, null)).isEmpty();
    }

    // ------------------------------------------------------------------ 辅助

    private static Announcement draft(long id, int minReadSeconds) {
        Announcement announcement = Announcement.draft("标题", "正文", AnnouncementLevel.URGENT,
                BASE.minusSeconds(60), null, minReadSeconds, false, false, null, OPERATOR_ID);
        TestFixtures.setId(announcement, id);
        TestFixtures.setCreatedAt(announcement, BASE);
        return announcement;
    }

    private static Announcement published(long id, int minReadSeconds) {
        Announcement announcement = draft(id, minReadSeconds);
        announcement.publish(BASE);
        return announcement;
    }
}
