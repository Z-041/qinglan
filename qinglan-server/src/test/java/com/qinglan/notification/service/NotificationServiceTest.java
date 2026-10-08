package com.qinglan.notification.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.common.store.InMemoryEphemeralStore;
import com.qinglan.common.web.CursorPage;
import com.qinglan.notification.dto.NotificationItemResponse;
import com.qinglan.notification.entity.Notification;
import com.qinglan.notification.repository.NotificationRepository;
import com.qinglan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通知服务的单元测试。
 *
 * <p>盯四件事：自触发静默不发、收件人缺失不发、正常落库为未读且标题非空、
 * 单条已读只认「我的通知」且对已读幂等、游标非法必须拒绝。</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final long RECIPIENT_ID = 7L;
    private static final long ACTOR_ID = 8L;
    private static final long NOTIFICATION_ID = 100L;
    private static final Instant BASE = Instant.parse("2026-10-07T00:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    /** 真实内存实现：去重键的组成（link 维度、type 维度）只有走真实实现才能被真正验证 */
    private final EphemeralStore ephemeralStore = new InMemoryEphemeralStore();

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, ephemeralStore);
    }

    @Test
    @DisplayName("自触发不发通知：收件人即触发者时不落库")
    void shouldSkipSelfNotification() {
        notificationService.notify(RECIPIENT_ID, RECIPIENT_ID,
                new NotificationMessage(NotificationType.LIKE, "有人赞了你的帖子", null, "/posts/1"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("收件人为空时不落库")
    void shouldSkipWhenRecipientMissing() {
        notificationService.notify(null, ACTOR_ID,
                new NotificationMessage(NotificationType.COMMENT, "有人评论了你的帖子", "内容", "/posts/1"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("正常落库：创建即未读，标题取自消息")
    void shouldPersistUnreadNotification() {
        notificationService.notify(RECIPIENT_ID, ACTOR_ID,
                new NotificationMessage(NotificationType.COMMENT, "有人评论了你的帖子", "同问", "/posts/1"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT);
        assertThat(saved.getTitle()).isEqualTo("有人评论了你的帖子");
        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    @DisplayName("标题为空时按类型给默认文案，避免出现无标题通知")
    void shouldFallbackTitleWhenBlank() {
        notificationService.notify(RECIPIENT_ID, ACTOR_ID,
                new NotificationMessage(NotificationType.LIKE, "   ", null, "/posts/1"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isNotBlank();
    }

    // ---------------------------------------------------------- H2 点赞通知去重

    @Test
    @DisplayName("H2 去重：同一接收者 + LIKE + 同一 link 在窗口内连续触发两次，只落一条")
    void shouldDeduplicateLikeWithinWindow() {
        NotificationMessage like =
                new NotificationMessage(NotificationType.LIKE, "有人赞了你的帖子", null, "/posts/1");

        // 复现「点赞 → 取消 → 再点赞」：两次触发携带完全相同的目标
        notificationService.notify(RECIPIENT_ID, ACTOR_ID, like);
        notificationService.notify(RECIPIENT_ID, ACTOR_ID, like);

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("H2 去重：不同 link 视为不同目标，两条都落")
    void shouldNotDeduplicateDifferentLink() {
        notificationService.notify(RECIPIENT_ID, ACTOR_ID,
                new NotificationMessage(NotificationType.LIKE, "有人赞了你的帖子", null, "/posts/1"));
        notificationService.notify(RECIPIENT_ID, ACTOR_ID,
                new NotificationMessage(NotificationType.LIKE, "有人赞了你的帖子", null, "/posts/2"));

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("H2 去重：超出窗口后两条都落（窗口过期由 EphemeralStore 的 TTL 释放，这里模拟键已被释放）")
    void shouldPersistAgainAfterWindowExpires() {
        // 窗口长度由存储的 TTL 决定，单元测试不去真实等待 1 小时：用一个「每次占用都成功」的桩
        // 代表窗口已过期、键已被释放——这正是 TTL 到期后的既有语义（见 InMemoryEphemeralStoreTest）
        EphemeralStore windowReleasedStore = mock(EphemeralStore.class);
        when(windowReleasedStore.markUsedOnce(anyString(), any(Duration.class))).thenReturn(true, true);
        NotificationService service = new NotificationService(notificationRepository, windowReleasedStore);
        NotificationMessage like =
                new NotificationMessage(NotificationType.LIKE, "有人赞了你的帖子", null, "/posts/1");

        service.notify(RECIPIENT_ID, ACTOR_ID, like);
        service.notify(RECIPIENT_ID, ACTOR_ID, like);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("H2 去重：非 LIKE 类型不受去重影响，重复触发两条都落")
    void shouldNotDeduplicateNonLikeType() {
        NotificationMessage review = new NotificationMessage(
                NotificationType.REVIEW_RESULT, "你的内容有新的审核结果", "已通过", "/posts/1");

        notificationService.notify(RECIPIENT_ID, ACTOR_ID, review);
        notificationService.notify(RECIPIENT_ID, ACTOR_ID, review);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("H2 去重：link 为 null 时不参与去重，两条都落")
    void shouldNotDeduplicateWhenLinkMissing() {
        NotificationMessage like =
                new NotificationMessage(NotificationType.LIKE, "有人赞了你的帖子", null, null);

        notificationService.notify(RECIPIENT_ID, ACTOR_ID, like);
        notificationService.notify(RECIPIENT_ID, ACTOR_ID, like);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("未读计数走 countByUserIdAndReadAtIsNull")
    void shouldCountUnreadViaRepository() {
        when(notificationRepository.countByUserIdAndReadAtIsNull(RECIPIENT_ID)).thenReturn(3L);

        assertThat(notificationService.unreadCount(RECIPIENT_ID)).isEqualTo(3L);
        verify(notificationRepository).countByUserIdAndReadAtIsNull(RECIPIENT_ID);
    }

    @Test
    @DisplayName("标记已读：查不到（含别人的通知）时返回「通知不存在」")
    void shouldRejectMarkReadForForeignNotification() {
        when(notificationRepository.findByIdAndUserId(NOTIFICATION_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(RECIPIENT_ID, NOTIFICATION_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("标记已读对已读通知幂等：readAt 不被覆盖")
    void shouldNotOverwriteReadAtWhenAlreadyRead() {
        Notification alreadyRead = notification(NOTIFICATION_ID, BASE);
        when(notificationRepository.findByIdAndUserId(NOTIFICATION_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(alreadyRead));

        notificationService.markRead(RECIPIENT_ID, NOTIFICATION_ID);

        assertThat(alreadyRead.getReadAt()).isEqualTo(BASE);
    }

    @Test
    @DisplayName("列表游标非法时拒绝，且不触碰仓储")
    void shouldRejectMalformedCursor() {
        assertThatThrownBy(() -> notificationService.list(RECIPIENT_ID, "not-a-cursor"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> notificationService.list(RECIPIENT_ID, "0"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("列表首页：按 id 倒序取一页并给出下一页游标")
    void shouldListFirstPageWithNextCursor() {
        when(notificationRepository.findByUserIdOrderByIdDesc(eq(RECIPIENT_ID), any(Pageable.class)))
                .thenReturn(List.of(notification(3L, null), notification(2L, null)));

        CursorPage<NotificationItemResponse> page = notificationService.list(RECIPIENT_ID, null);

        assertThat(page.items()).extracting(NotificationItemResponse::id).containsExactly(3L, 2L);
        assertThat(page.nextCursor()).isNull();
    }

    // ------------------------------------------------------------------ 辅助

    private static Notification notification(long id, Instant readAt) {
        Notification notification = Notification.of(RECIPIENT_ID, NotificationType.COMMENT,
                "有人评论了你的帖子", "内容", "/posts/1");
        if (readAt != null) {
            notification.markRead(readAt);
        }
        TestFixtures.setId(notification, id);
        TestFixtures.setCreatedAt(notification, BASE);
        return notification;
    }
}
