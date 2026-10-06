package com.qinglan.notification.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.web.CursorPage;
import com.qinglan.notification.dto.NotificationItemResponse;
import com.qinglan.notification.entity.Notification;
import com.qinglan.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 个人通知聚合：发出、列表、未读计数、单条已读、全部已读。
 *
 * <p>本类同时是 {@link NotificationPort} 的实现——跨模块「发出通知」这一能力的**唯一实现点**。
 * comment / post / review 只依赖 {@code common.notify} 的端口，不依赖本模块，
 * 因此依赖图无新增业务边（见 AGENTS.md 第三章、计划 D1）。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationPort {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;

    /**
     * 发出通知。收件人为空、或收件人即触发者时**静默跳过**——自己评论 / 点赞自己的帖子不发通知，
     * 该判断集中在此处，调用方不必各自处理（与 {@link NotificationPort} 的契约一致）。
     *
     * <p>与主流程同事务，不吞异常：通知落库失败要跟着回滚，与 {@code AuditLogService} 的既有做法一致。</p>
     */
    @Override
    @Transactional
    public void notify(Long recipientId, Long actorId, NotificationMessage message) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        notificationRepository.save(Notification.of(
                recipientId,
                message.type(),
                resolveTitle(message.type(), message.title()),
                message.body(),
                message.link()));
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationItemResponse> list(Long userId, String cursor) {
        Pageable pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE + 1);
        List<Notification> found = cursor == null || cursor.isBlank()
                ? notificationRepository.findByUserIdOrderByIdDesc(userId, pageable)
                : notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, parseCursor(cursor), pageable);

        boolean hasMore = found.size() > DEFAULT_PAGE_SIZE;
        List<Notification> page = hasMore ? List.copyOf(found.subList(0, DEFAULT_PAGE_SIZE)) : found;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        List<NotificationItemResponse> items = page.stream().map(NotificationService::toItem).toList();
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;
        return CursorPage.of(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /** 单条已读。定位带 userId，命中别人（或不存在）一律按「通知不存在」返回，不泄漏存在性。 */
    @Transactional
    public void markRead(Long userId, Long id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(Instant.now());
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    // ---------------------------------------------------------------- 内部方法

    /** 标题不得为空：调用方未给或给了空白时按类型给默认文案，避免消息中心出现无标题行。 */
    private static String resolveTitle(NotificationType type, String title) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return switch (type) {
            case COMMENT -> "有人评论了你的帖子";
            case LIKE -> "有人赞了你的帖子";
            case REVIEW_RESULT -> "你的内容有新的审核结果";
            case REPORT_FEEDBACK -> "你举报的内容有处理结果";
        };
    }

    private static NotificationItemResponse toItem(Notification notification) {
        return new NotificationItemResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    private static long parseCursor(String raw) {
        try {
            long cursor = Long.parseLong(raw.trim());
            if (cursor <= 0) {
                throw new NumberFormatException("游标必须为正整数");
            }
            return cursor;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }
}
