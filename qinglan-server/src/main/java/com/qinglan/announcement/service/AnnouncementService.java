package com.qinglan.announcement.service;

import com.qinglan.announcement.dto.AdminAnnouncementItemResponse;
import com.qinglan.announcement.dto.AnnouncementSummaryResponse;
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
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.common.web.CompositeCursor;
import com.qinglan.common.web.CompositeCursor.KeyKind;
import com.qinglan.common.web.CursorPage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 公告：草稿 / 发布 / 撤回 / 待读一条 / 标记已读 / 历史列表。
 *
 * <p>两条不变量在本服务的落点：</p>
 * <ul>
 *   <li>#6 强制阅读：标记已读只此一个入口，且服务端校验真实停留时长（不足即拒绝、不落库）</li>
 *   <li>#7 审计只增不改删：创建 / 发布 / 撤回都写 {@code review_log}（ANNOUNCEMENT_* 三个动作）</li>
 * </ul>
 *
 * <p>角色判断不在本层：发布 / 撤回仅 SUPER_ADMIN 由 Controller 的 {@code @PreAuthorize} 兜住，
 * 服务层只做状态合法与频率上限这类业务判断。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final long MILLIS_PER_SECOND = 1000L;
    /** 日界线时区：与管理员直觉一致，与 AdminStatsService 的看板口径保持一致 */
    private static final ZoneId DAY_ZONE = ZoneId.of("Asia/Shanghai");
    /** 标记已读的限流键（AGENTS.md 约定 ql:{domain}:{id}）。该接口匿名开放，必须限流 */
    private static final String READ_RATE_KEY_PREFIX = "ql:announcement:read-rate:";
    /** 设备标识只允许这几种字符，避免把任意字符串写进列与日志 */
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** 弹窗用：所有级别的未读公告都要弹（设计方案 5.5） */
    private static final List<AnnouncementLevel> ALL_LEVELS = List.of(AnnouncementLevel.values());
    /** 发帖 gate 用：只有紧急公告拦截发帖（设计方案 5.4） */
    private static final List<AnnouncementLevel> URGENT_ONLY = List.of(AnnouncementLevel.URGENT);

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final AuditLogService auditLogService;
    private final AnnouncementProperties properties;
    private final EphemeralStore ephemeralStore;

    /** 建草稿。发布权限在 Controller 收口（仅 SUPER_ADMIN 可发布，见计划 D8）。 */
    @Transactional
    public Long createDraft(Long operatorId, CreateAnnouncementRequest request) {
        int minReadSeconds = request.minReadSeconds() != null
                ? request.minReadSeconds()
                : properties.minReadSeconds();
        Announcement announcement = Announcement.draft(
                request.title().trim(),
                request.content().trim(),
                request.level(),
                request.startAt(),
                request.endAt(),
                minReadSeconds,
                request.requireScrollToEnd(),
                request.requireConfirm(),
                request.supersedesId(),
                operatorId);
        Announcement saved = announcementRepository.save(announcement);
        auditLogService.record(AuditTargetType.ANNOUNCEMENT, saved.getId(),
                AuditAction.ANNOUNCEMENT_CREATE, operatorId, null);
        log.info("创建公告草稿 announcementId={} level={}，操作人 operatorId={}",
                saved.getId(), request.level(), operatorId);
        return saved.getId();
    }

    /** 管理端建草稿后取回完整项（Controller 返回 DTO，不返回 Entity）。 */
    @Transactional(readOnly = true)
    public AdminAnnouncementItemResponse adminItem(Long id) {
        return toAdminItem(requireAnnouncement(id));
    }

    /**
     * 发布。状态合法由 {@link Announcement#publish(Instant)} 裁决（不变量式：非 DRAFT 抛状态非法）；
     * 频率上限（当日发布数）在此校验（计划 D9）。
     */
    @Transactional
    public void publish(Long operatorId, Long id) {
        Announcement announcement = requireAnnouncement(id);
        Instant now = Instant.now();
        // 仅当确为可发布的草稿时才做频率检查，避免「REVOKED 再发布」被频率上限掩盖成 429，掩盖真实状态错误
        if (announcement.getStatus() == AnnouncementStatus.DRAFT
                && publishedToday(now) >= properties.maxPublishesPerDay()) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_RATE_LIMITED);
        }
        announcement.publish(now);
        auditLogService.record(AuditTargetType.ANNOUNCEMENT, id,
                AuditAction.ANNOUNCEMENT_PUBLISH, operatorId, null);
        log.info("发布公告 announcementId={}，操作人 operatorId={}", id, operatorId);
    }

    /** 撤回。仅 PUBLISHED 可撤回，不删已读记录（计划 D7）。 */
    @Transactional
    public void revoke(Long operatorId, Long id) {
        Announcement announcement = requireAnnouncement(id);
        announcement.revoke(Instant.now());
        auditLogService.record(AuditTargetType.ANNOUNCEMENT, id,
                AuditAction.ANNOUNCEMENT_REVOKE, operatorId, null);
        log.info("撤回公告 announcementId={}，操作人 operatorId={}", id, operatorId);
    }

    /**
     * 待读一条：供公开接口（弹窗）使用。返回优先级最高的一条，无未读返回空。
     *
     * <p>不限制级别：任何未读公告都值得弹一次（设计方案 5.5「未读即弹」）。</p>
     *
     * @param userId   登录用户 id；游客为 null
     * @param deviceId 游客设备标识；未传为 null
     */
    @Transactional(readOnly = true)
    public Optional<PendingAnnouncementResponse> findPending(Long userId, String deviceId) {
        return findPendingAmong(userId, deviceId, ALL_LEVELS);
    }

    /**
     * 待读的<b>紧急</b>公告：**只给发帖 gate 用**（设计方案 5.4）。
     *
     * <p>规格原文：「存在未读的 URGENT 公告时，POST /api/posts 返回 428……
     * 是否启用此拦截由 {@code level=URGENT} 决定，<b>普通公告不拦截</b>，避免过度打扰」。
     * 若这里复用 {@link #findPending}（不限级别），一条普通活动公告就会挡住全站发帖，
     * 与该规格相反。</p>
     */
    @Transactional(readOnly = true)
    public Optional<PendingAnnouncementResponse> findPendingUrgent(Long userId) {
        // 发帖必然已登录，设备维度无需参与
        return findPendingAmong(userId, null, URGENT_ONLY);
    }

    private Optional<PendingAnnouncementResponse> findPendingAmong(
            Long userId, String deviceId, Collection<AnnouncementLevel> levels) {
        // 已读排除维度只取其一：登录用户按账号判，只有游客才按设备判。
        // 若对登录用户也按设备排除，同一台电脑上会出现「甲以游客身份读过 → 乙登录后被甲的记录挡掉」，
        // 而强制阅读里「漏看」远比「多看一次」危险，故登录态一律忽略设备维度（计划 D5 修订）。
        String gateDeviceId = userId == null ? normalizeDeviceId(deviceId) : null;
        return announcementRepository.findPending(
                        AnnouncementStatus.PUBLISHED,
                        levels,
                        AnnouncementLevel.URGENT,
                        AnnouncementLevel.NORMAL,
                        Instant.now(),
                        userId,
                        gateDeviceId,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toPending);
    }

    /**
     * 标记已读——不变量 #6 的核心。
     *
     * <p>先确认公告存在（否则 404）；三个强制条件（设计方案 5.2 的 AND）任一不满足即抛 400 且
     * <b>不落库</b>，因此不存在「打开弹窗即已读」的路径；条件齐备时按维度更新或新增已读记录
     * （唯一键幂等）。</p>
     *
     * @param deviceIdQuery 查询参数里的 deviceId；请求体里的 deviceId 优先
     */
    @Transactional
    public void markRead(Long id, Long userId, String deviceIdQuery, MarkReadRequest request) {
        Announcement announcement = requireAnnouncement(id);
        // 只允许对「已发布」的公告标记已读。公告 id 自增可枚举，少了这道校验就能对草稿/已撤回/不存在的 id
        // 任意写入已读行，把一个匿名写接口变成刷表入口
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        String deviceId = resolveDeviceId(request.deviceId(), deviceIdQuery);
        if (userId == null && deviceId == null) {
            // 登录用户与游客至少要能标识一个，否则记录无意义且会撞唯一键
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        // 限流放在业务校验之前：非法请求同样消耗配额，否则可以用「时长不足但格式合法」的请求反复打这个接口
        String rateSubject = userId != null ? "u" + userId : "d" + deviceId;
        long attempts = ephemeralStore.increment(READ_RATE_KEY_PREFIX + rateSubject, properties.readRateWindow());
        if (attempts > properties.maxReadsPerWindow()) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_READ_TOO_FREQUENT);
        }
        if (request.durationMs() < announcement.getMinReadSeconds() * MILLIS_PER_SECOND) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_READ_TOO_EARLY);
        }
        // 另外两个条件：需滚动到底、需勾选已了解。二者由客户端观测，服务端只能做声明式校验，
        // 所以「真强制」仍靠 428 兜底（设计方案 5.4、计划 D6）；但既然拿到了信号就必须校验，
        // 否则前端一旦实现有误，服务端会把「没读完」也记成已读。
        if ((announcement.isRequireScrollToEnd() && !request.scrolledToEnd())
                || (announcement.isRequireConfirm() && !request.confirmed())) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_READ_UNCONFIRMED);
        }
        Instant now = Instant.now();
        Optional<AnnouncementRead> existing = userId != null
                ? announcementReadRepository.findByAnnouncementIdAndUserId(id, userId)
                : announcementReadRepository.findByAnnouncementIdAndDeviceId(id, deviceId);
        if (existing.isPresent()) {
            existing.get().updateEvidence(now, request.durationMs(), request.confirmed());
            return;
        }
        // 登录用户只填 user_id、游客只填 device_id：两个唯一键各自作用，不会互相撞车
        AnnouncementRead read = userId != null
                ? AnnouncementRead.of(id, userId, null, now, request.durationMs(), request.confirmed())
                : AnnouncementRead.of(id, null, deviceId, now, request.durationMs(), request.confirmed());
        announcementReadRepository.save(read);
        log.info("标记公告已读 announcementId={} 停留时长ms={}", id, request.durationMs());
    }

    /** 学生端公告历史：已发布公告，游标按 id 倒序；批量标「我是否已读」避免 N+1 */
    @Transactional(readOnly = true)
    public CursorPage<AnnouncementSummaryResponse> history(String cursor, Long userId, String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        Pageable pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE + 1);
        List<Announcement> found = announcementRepository.findHistory(
                AnnouncementStatus.PUBLISHED, parseCursor(cursor), pageable);

        boolean hasMore = found.size() > DEFAULT_PAGE_SIZE;
        List<Announcement> page = hasMore ? List.copyOf(found.subList(0, DEFAULT_PAGE_SIZE)) : found;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        Set<Long> readIds = loadReadAnnouncementIds(
                page.stream().map(Announcement::getId).toList(), userId, normalizedDeviceId);
        List<AnnouncementSummaryResponse> items = page.stream()
                .map(announcement -> toSummary(announcement, readIds.contains(announcement.getId())))
                .toList();
        String nextCursor = hasMore ? CompositeCursor.format(page.get(page.size() - 1).getId()) : null;
        return CursorPage.of(items, nextCursor);
    }

    /** 管理端列表：全状态、键集分页 */
    @Transactional(readOnly = true)
    public CursorPage<AdminAnnouncementItemResponse> adminList(String cursor) {
        Pageable pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE + 1);
        List<Announcement> found = announcementRepository.findAdminPage(parseCursor(cursor), pageable);

        boolean hasMore = found.size() > DEFAULT_PAGE_SIZE;
        List<Announcement> page = hasMore ? List.copyOf(found.subList(0, DEFAULT_PAGE_SIZE)) : found;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        List<AdminAnnouncementItemResponse> items = page.stream().map(this::toAdminItem).toList();
        String nextCursor = hasMore ? CompositeCursor.format(page.get(page.size() - 1).getId()) : null;
        return CursorPage.of(items, nextCursor);
    }

    // ---------------------------------------------------------------- 内部方法

    private Announcement requireAnnouncement(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }

    /**
     * 当日**发布过**的公告数（含已被撤回的）。日界线用 Asia/Shanghai，与 {@code AdminStatsService} 的看板口径一致。
     *
     * <p>不按「当前状态 = PUBLISHED」统计：撤回会改状态，「发布 → 撤回 → 再发布」就能让计数回落、
     * 绕过每日上限。上限约束的是发布这个动作。</p>
     */
    private long publishedToday(Instant now) {
        Instant dayStart = now.atZone(DAY_ZONE).toLocalDate().atStartOfDay(DAY_ZONE).toInstant();
        return announcementRepository.countByPublishedAtGreaterThanEqual(dayStart);
    }

    /** 请求体 deviceId 优先，其次查询参数；两者都空返回 null。超长直接拒绝而不是让数据库报错 */
    private String resolveDeviceId(String fromBody, String fromQuery) {
        String candidate = (fromBody != null && !fromBody.isBlank()) ? fromBody : fromQuery;
        return normalizeDeviceId(candidate);
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        String trimmed = deviceId.trim();
        if (!DEVICE_ID_PATTERN.matcher(trimmed).matches()) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        return trimmed;
    }

    /**
     * 批量取已读公告 id。与 {@link #findPending} 同一口径：**登录用户只看账号维度，只有游客才看设备维度**
     * ——若两者都看，共用设备上他人的游客记录会把自己的已读状态污染，出现「公告已读但本人从没看过」。
     */
    private Set<Long> loadReadAnnouncementIds(Collection<Long> announcementIds, Long userId, String deviceId) {
        Set<Long> readIds = new HashSet<>();
        if (userId != null) {
            announcementReadRepository.findByAnnouncementIdInAndUserId(announcementIds, userId)
                    .forEach(read -> readIds.add(read.getAnnouncementId()));
            return readIds;
        }
        if (deviceId != null) {
            announcementReadRepository.findByAnnouncementIdInAndDeviceId(announcementIds, deviceId)
                    .forEach(read -> readIds.add(read.getAnnouncementId()));
        }
        return readIds;
    }

    private PendingAnnouncementResponse toPending(Announcement announcement) {
        return new PendingAnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getLevel(),
                announcement.getMinReadSeconds(),
                announcement.isRequireScrollToEnd(),
                announcement.isRequireConfirm(),
                announcement.getPublishedAt());
    }

    private AnnouncementSummaryResponse toSummary(Announcement announcement, boolean read) {
        return new AnnouncementSummaryResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getLevel(),
                announcement.getPublishedAt(),
                read);
    }

    private AdminAnnouncementItemResponse toAdminItem(Announcement announcement) {
        return new AdminAnnouncementItemResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getLevel(),
                announcement.getStatus(),
                announcement.getStartAt(),
                announcement.getEndAt(),
                announcement.getMinReadSeconds(),
                announcement.isRequireScrollToEnd(),
                announcement.isRequireConfirm(),
                announcement.getPublishedAt(),
                announcement.getRevokedAt(),
                announcement.getCreatedAt());
    }

    private long parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            // 首页游标取 Long.MAX_VALUE，省掉 SQL 里的 null 分支
            return Long.MAX_VALUE;
        }
        return CompositeCursor.parse(raw, KeyKind.POSITIVE_LONG)[0];
    }
}
