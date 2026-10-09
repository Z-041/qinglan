package com.qinglan.notification.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.common.web.CompositeCursor;
import com.qinglan.common.web.CompositeCursor.KeyKind;
import com.qinglan.common.web.CursorPage;
import com.qinglan.notification.dto.NotificationItemResponse;
import com.qinglan.notification.entity.Notification;
import com.qinglan.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    /**
     * 去重策略表：**只登记点赞**，其余类型不参与去重。
     *
     * <p>只有点赞存在「点赞 → 取消 → 再点赞」的循环：每次重新点赞都会新建点赞记录并触发通知，
     * 而通知表只增不删（{@link Notification} 无删除接口），脚本循环即可刷爆作者的通知列表。
     * 审核结果 / 举报反馈这类通知即使内容相同也必须如实送达，评论也属低频，均不去重。
     * 将来要放开某类型只改本表，不要写成散落的 if。</p>
     */
    private static final Map<NotificationType, Duration> DEDUP_WINDOWS =
            Map.of(NotificationType.LIKE, Duration.ofHours(1));

    /** 去重键前缀（AGENTS.md Redis key 约定 ql:{domain}:{id}）：接收者 + 类型 + 目标(link) */
    private static final String DEDUP_KEY_PREFIX = "ql:notify:dedup:";

    private final NotificationRepository notificationRepository;
    private final EphemeralStore ephemeralStore;

    /**
     * 发出通知。收件人为空、或收件人即触发者时**静默跳过**——自己评论 / 点赞自己的帖子不发通知，
     * 该判断集中在此处，调用方不必各自处理（与 {@link NotificationPort} 的契约一致）。
     *
     * <p>与主流程同事务，不吞异常：通知落库失败要跟着回滚，与 {@code AuditLogService} 的既有做法一致。</p>
     *
     * <p>命中 {@link #DEDUP_WINDOWS} 的重复触发（同接收者 + 同类型 + 同 link，且在窗口内）静默丢弃，
     * 不发第二条——这是点赞脚本刷爆通知的唯一收口点。</p>
     */
    @Override
    @Transactional
    public void notify(Long recipientId, Long actorId, NotificationMessage message) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        if (isDuplicateWithinWindow(recipientId, message)) {
            return;
        }
        notificationRepository.save(Notification.of(
                recipientId,
                message.type(),
                resolveTitle(message.type(), message.title()),
                message.body(),
                message.link()));
    }

    /**
     * 是否处于去重窗口内：同一接收者、同一类型、同一目标（用 {@code link} 判定）在窗口内只落一条。
     *
     * <p>{@code link} 为 null 时**不去重**——没有可判定的「目标」。若把 null 也归并成同一个键，
     * 会把所有无链接通知（审核结果 / 举报反馈等）互相误判为同一目标而互相吞掉。</p>
     *
     * <p>用 {@link EphemeralStore#markUsedOnce} 的「首次占用」语义：首次 true（应落库），
     * 窗口内重复 false（应丢弃）。窗口长度由 TTL 提供，memory / redis 两种实现共用同一套逻辑。</p>
     */
    private boolean isDuplicateWithinWindow(Long recipientId, NotificationMessage message) {
        Duration window = DEDUP_WINDOWS.get(message.type());
        String link = message.link();
        if (window == null || link == null) {
            return false;
        }
        String key = DEDUP_KEY_PREFIX + recipientId + ":" + message.type() + ":" + link;
        return !ephemeralStore.markUsedOnce(key, window);
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
        String nextCursor = hasMore ? CompositeCursor.format(page.get(page.size() - 1).getId()) : null;
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
        return CompositeCursor.parse(raw, KeyKind.POSITIVE_LONG)[0];
    }
}
