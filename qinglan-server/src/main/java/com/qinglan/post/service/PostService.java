package com.qinglan.post.service;

import com.qinglan.announcement.service.AnnouncementService;
import com.qinglan.common.anonymous.AnonymousCodeService;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.error.PreconditionRequiredException;
import com.qinglan.common.interaction.LikeCount;
import com.qinglan.common.interaction.LikeRecord;
import com.qinglan.common.interaction.LikeRepository;
import com.qinglan.common.interaction.LikeStateResponse;
import com.qinglan.common.interaction.LikeTargetType;
import com.qinglan.common.moderation.ContentGuard;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.common.security.Viewer;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.config.PostProperties;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.post.dto.MyPostItemResponse;
import com.qinglan.post.dto.PostDetailResponse;
import com.qinglan.post.dto.PostSort;
import com.qinglan.post.dto.PostSummaryResponse;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostImage;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.RiskLevel;
import com.qinglan.post.entity.Topic;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.PostImageRepository;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.TopicRepository;
import com.qinglan.post.repository.UploadedFileRepository;
import com.qinglan.user.entity.User;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子主流程：发布、信息流、详情、点赞。
 *
 * <p>审核侧动作（放行 / 驳回 / 下架 / 恢复 / 隐藏联系方式）全在 {@code review} 模块，
 * 本服务不再持有任何管理端入口——帖子模块只负责内容本身的读写。</p>
 *
 * <p>三条不变量在本服务的落点：</p>
 * <ul>
 *   <li>#1 匿名代号：插入拿到 post_id 后由 {@link AnonymousCodeService} 派生回填，
 *       对外 DTO 一律不含 authorId</li>
 *   <li>#2 联系方式：只由 {@link Post#contactVisibleTo} 判定，未认证 viewer 拿到 null；
 *       查看他人的联系方式写审计日志</li>
 *   <li>#5 审核状态：一切状态变更都走 {@link Post#transitionTo}，本服务不直接写 status</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_CONTACT_LENGTH = 200;
    /** 热门分数的时间衰减尺度（小时）：e^(-年龄/72h)，方案给定 */
    private static final double HOT_DECAY_HOURS = 72.0;
    /** 热门分数放大倍数：把浮点分数取整成可稳定比较的长整型游标键 */
    private static final long HOT_SCORE_SCALE = 1_000_000L;

    /**
     * 联系方式查看审计的限流键前缀（AGENTS.md Redis key 约定 {@code ql:{domain}:{id}}）。
     */
    private static final String CONTACT_VIEW_RATE_KEY_PREFIX = "ql:post:contact-view:";

    /**
     * 单个用户在一个窗口内最多写多少条 {@code CONTACT_VIEW} 审计。
     *
     * <p>限流按<b>用户</b>而非「用户 + 帖子」。详情页每次访问都会写一条审计（不变量 #2），脚本批量
     * 抓取可刷爆 {@code review_log} 并放大详情接口的写入。若按「用户 + 帖子」去重，同一人抓 1000 个
     * 不同帖子仍会写 1000 行，写入量不封顶；按用户维度才能把单位时间内的写入量真正压到上限 N。
     * 代价是同一人短时间内浏览多个帖子只留部分痕迹，但审计目的正是识别「异常批量抓取」，用户维度已足够。</p>
     *
     * <p>包内可见以便测试直接引用阈值，不在测试里重复魔法数。</p>
     */
    static final int CONTACT_VIEW_MAX_PER_WINDOW = 60;

    /** 联系方式查看审计的限流窗口（与 {@link #CONTACT_VIEW_MAX_PER_WINDOW} 成对，从首次访问起算） */
    private static final Duration CONTACT_VIEW_RATE_WINDOW = Duration.ofMinutes(1);

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final TopicRepository topicRepository;
    private final LikeRepository likeRepository;
    private final AnonymousCodeService anonymousCodeService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final PostProperties postProperties;
    private final ContentGuard contentGuard;
    private final AnnouncementService announcementService;
    private final NotificationPort notificationPort;
    private final EphemeralStore ephemeralStore;
    private final ImageUrlAssembler imageUrlAssembler;

    /**
     * 发布。落 Pending 而非直接可见——主帖先审后发，是设计方案第一章的红线 2。
     */
    @Transactional
    public PostDetailResponse create(Long authorId, CreatePostRequest request) {
        User author = userService.requireUsableById(authorId);
        // 强制阅读的服务端兜底（设计方案 5.4）：存在未读**紧急**公告时不允许发帖。
        // 前端禁用只是体验层，绕过 UI 也发不了帖。428 携带公告内容供前端直接弹窗。
        // 只查 URGENT：规格明确「普通公告不拦截」，用不限级别的 findPending 会让一条活动公告挡住全站发帖。
        announcementService.findPendingUrgent(authorId).ifPresent(pending -> {
            throw new PreconditionRequiredException(ErrorCode.PRECONDITION_REQUIRED, pending);
        });
        assertCanPost(author);
        Topic topic = requireEnabledTopic(request.topicId());

        String contact = normalizeContact(request.contact());
        if (topic.isRequiresContact() && contact == null) {
            throw new BizException(ErrorCode.CONTACT_REQUIRED);
        }
        requireAllowedContent(request.content(), contact);
        ValidatedImages images = validateImages(request.images(), authorId);

        Post post = Post.draft(authorId, topic.getId(), request.content().trim(), contact);
        post.submit(RiskLevel.LOW);
        Post saved = postRepository.save(post);
        // 代号依赖 post_id，只能在 INSERT 拿到主键之后回填（不变量 #1）
        saved.assignAnonymousCode(
                anonymousCodeService.deriveCode(saved.getId(), authorId, author.getAnonymousSalt()));
        saveImages(saved.getId(), images.files());

        log.info("用户发布帖子 postId={} topicId={}", saved.getId(), topic.getId());
        // 待审帖图属私有：装配时签发签名 URL，作者在详情页/我的帖子里才能立刻看到自己的图
        return toDetail(saved, topic.getName(), topic.getRules(),
                imageUrlAssembler.forPost(saved.getStatus(), images.urls()), 0L, false, new Viewer(authorId));
    }

    /**
     * 信息流。只出审核通过的帖子。
     *
     * <p>{@code sort=LATEST}（默认）按 id 倒序、游标为单个 id，语义与游标格式均与既有行为**完全一致**；
     * {@code sort=HOT} 按热度排序，见 {@link #hotFeed}。</p>
     *
     * <p>页大小固定，不对外暴露 size 参数——AGENTS.md 第三章要求列表接口只用游标分页。</p>
     */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryResponse> listFeed(Long topicId, PostSort sort, String cursorRaw, Viewer viewer) {
        return sort == PostSort.HOT
                ? hotFeed(topicId, cursorRaw, viewer)
                : latestFeed(topicId, cursorRaw, viewer);
    }

    /** 最新排序：游标是帖子 id，与历史实现逐字一致（回归基线，不得改动语义与游标格式） */
    private CursorPage<PostSummaryResponse> latestFeed(Long topicId, String cursorRaw, Viewer viewer) {
        long cursor = parseCursor(cursorRaw);
        // 多取一条用于判断是否还有下一页，避免额外 count 查询
        Pageable pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE + 1);
        List<Post> found = topicId == null
                ? postRepository.findFeed(PostStatus.APPROVED, cursor, pageable)
                : postRepository.findFeedByTopic(PostStatus.APPROVED, topicId, cursor, pageable);

        boolean hasMore = found.size() > DEFAULT_PAGE_SIZE;
        List<Post> page = hasMore ? List.copyOf(found.subList(0, DEFAULT_PAGE_SIZE)) : found;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Map<Long, Long> likeCounts = loadLikeCounts(LikeTargetType.POST, postIds);
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;
        return CursorPage.of(assembleSummaries(page, likeCounts, viewer), nextCursor);
    }

    /**
     * 热门排序（学生端阶段 3）。权重 {@code score = (点赞数 × 1 + 评论数 × 2) × e^(-年龄/72h)}。
     *
     * <p><b>游标方案（取舍 ①，已选定）：请求时刻统一算分 + 「(scale 后的分数, id)」复合游标，
     * 接受翻页期间的轻微漂移。</b>理由：热度分随点赞/评论实时变化，无法用稳定游标；物化热度列虽能
     * 让游标稳定，但引入定时重算、写放大与延迟（取舍 ②），对当前规模是过度设计。故选择无 schema 变更、
     * 无需后台任务的一条。</p>
     *
     * <p><b>已知局限（翻页时分数变了会怎样）</b>：因分数是每次请求现算的，两次请求之间若有新点赞，
     * 某条帖子可能位移到游标另一侧——表现为翻页时**偶尔漏掉或重复某条**。这是取舍 ① 的固有代价，
     * 在前端以「热门是探索式浏览」为前提可接受；要消除它需改用物化分数列（取舍 ②）。</p>
     *
     * <p>候选集由「时间窗口」与「条数上限」双重界定（{@code qinglan.post.hot-window} /
     * {@code hot-max-candidates}），避免全表算分；窗口外或超出上限的帖子不参与排名。</p>
     */
    private CursorPage<PostSummaryResponse> hotFeed(Long topicId, String cursorRaw, Viewer viewer) {
        HotCursor cursor = parseHotCursor(cursorRaw);
        Instant now = Instant.now();
        List<Post> candidates = postRepository.findHotCandidates(
                PostStatus.APPROVED, topicId, now.minus(postProperties.hotWindow()),
                PageRequest.of(0, postProperties.hotMaxCandidates()));
        if (candidates.isEmpty()) {
            return CursorPage.empty();
        }
        List<Long> candidateIds = candidates.stream().map(Post::getId).toList();
        Map<Long, Long> likeCounts = loadLikeCounts(LikeTargetType.POST, candidateIds);

        List<ScoredPost> ranked = candidates.stream()
                .map(post -> new ScoredPost(post, hotScoreMicros(post, likeCounts, now)))
                .sorted(Comparator.comparingLong(ScoredPost::scoreMicros).reversed()
                        .thenComparing(Comparator.comparingLong(
                                (ScoredPost scored) -> scored.post().getId()).reversed()))
                .toList();
        List<ScoredPost> afterCursor = cursor.first()
                ? ranked
                : ranked.stream().filter(scored -> scored.isAfter(cursor)).toList();
        if (afterCursor.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasMore = afterCursor.size() > DEFAULT_PAGE_SIZE;
        List<ScoredPost> page = hasMore
                ? List.copyOf(afterCursor.subList(0, DEFAULT_PAGE_SIZE)) : afterCursor;
        List<Post> posts = page.stream().map(ScoredPost::post).toList();
        String nextCursor = hasMore ? formatHotCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(assembleSummaries(posts, likeCounts, viewer), nextCursor);
    }

    /**
     * 热门分数（放大为微单位的长整型，作为游标比较的稳定键）。
     *
     * <p>用 {@code Math.round(score × 1e6)} 而非裸 {@code double} 比较：浮点相等判断在游标里不可靠，
     * 放大取整后同一时刻的分数是确定值，翻页比较可复现。时间衰减 {@code e^(-年龄/72h)}——72 小时是
     * 方案给定的半衰尺度。</p>
     */
    private long hotScoreMicros(Post post, Map<Long, Long> likeCounts, Instant now) {
        long likes = likeCounts.getOrDefault(post.getId(), 0L);
        long comments = post.getCommentCount();
        double weight = likes + comments * 2.0;
        double ageHours = Math.max(0.0,
                Duration.between(post.getCreatedAt(), now).toSeconds() / 3600.0);
        double score = weight * Math.exp(-ageHours / HOT_DECAY_HOURS);
        return Math.round(score * HOT_SCORE_SCALE);
    }

    /**
     * 「我的帖子」：仅本人可见，覆盖待审 / 已驳回 / 已下架 / 已通过全部状态，游标分页。
     *
     * <p>{@code reviewReason} 只在本人列表里返回，与详情接口语义一致；出参不含他人身份。</p>
     */
    @Transactional(readOnly = true)
    public CursorPage<MyPostItemResponse> listMine(Long userId, PostStatus status, String cursorRaw) {
        MineCursor cursor = parseMineCursor(cursorRaw);
        List<Post> found = postRepository.findMinePage(
                userId, status, cursor.createdAt(), cursor.id(), PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = found.size() > DEFAULT_PAGE_SIZE;
        List<Post> page = hasMore ? List.copyOf(found.subList(0, DEFAULT_PAGE_SIZE)) : found;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Map<Long, List<String>> imagesByPost = loadImageUrls(postIds);
        Map<Long, Long> likeCounts = loadLikeCounts(LikeTargetType.POST, postIds);
        Map<Long, String> topicNames = loadTopicNames(page);
        List<MyPostItemResponse> items = page.stream()
                .map(post -> new MyPostItemResponse(
                        post.getId(),
                        post.getTopicId(),
                        topicNames.get(post.getTopicId()),
                        post.getContent(),
                        imageUrlAssembler.forPost(post.getStatus(),
                                imagesByPost.getOrDefault(post.getId(), List.of())),
                        post.getAnonymousCode(),
                        post.getStatus(),
                        post.getReviewReason(),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        post.getCommentCount(),
                        post.getCreatedAt()))
                .toList();
        String nextCursor = hasMore ? formatMineCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    /** 一页信息流摘要的批量装配：三个批量查询覆盖整页（图片 / 是否已赞 / 话题名），杜绝 N+1 */
    private List<PostSummaryResponse> assembleSummaries(List<Post> page, Map<Long, Long> likeCounts, Viewer viewer) {
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Map<Long, List<String>> imagesByPost = loadImageUrls(postIds);
        Set<Long> likedIds = loadLikedIds(LikeTargetType.POST, postIds, viewer.userId());
        Map<Long, String> topicNames = loadTopicNames(page);
        return page.stream()
                .map(post -> new PostSummaryResponse(
                        post.getId(),
                        post.getTopicId(),
                        topicNames.get(post.getTopicId()),
                        post.getContent(),
                        imagesByPost.getOrDefault(post.getId(), List.of()),
                        post.getAnonymousCode(),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        likedIds.contains(post.getId()),
                        post.getCommentCount(),
                        viewer.is(post.getAuthorId()),
                        post.getCreatedAt()))
                .toList();
    }

    /**
     * 详情。需要写审计日志（查看联系方式），故不是只读事务。
     */
    @Transactional
    public PostDetailResponse detail(Long postId, Viewer viewer) {
        Post post = requireVisiblePost(postId, viewer.userId());
        Topic topic = topicRepository.findById(post.getTopicId()).orElse(null);
        // 私有帖图（待审/驳回/下架）装配为签名 URL：作者在浏览器里 <img> 不带令牌也能取到
        List<String> images = imageUrlAssembler.forPost(post.getStatus(),
                loadImageUrls(List.of(postId)).getOrDefault(postId, List.of()));
        long likeCount = likeRepository.countByTargetTypeAndTargetId(LikeTargetType.POST, postId);
        boolean liked = viewer.userId() != null
                && likeRepository.findByTargetTypeAndTargetIdAndUserId(
                LikeTargetType.POST, postId, viewer.userId()).isPresent();
        recordContactView(post, viewer);
        return toDetail(
                post,
                topic == null ? null : topic.getName(),
                topic == null ? null : topic.getRules(),
                images,
                likeCount,
                liked,
                viewer);
    }

    /** 点赞，幂等：已点过则不重复写入，直接返回当前状态 */
    @Transactional
    public LikeStateResponse like(Long postId, Long userId) {
        userService.requireUsableById(userId);
        Post post = requireVisiblePost(postId, userId);
        if (likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, postId, userId).isEmpty()) {
            // uk_like_target_user 是并发下的最终防线：重复插入会被数据库拒绝，不会产生第二条
            likeRepository.save(LikeRecord.of(LikeTargetType.POST, postId, userId));
            // 只在「本次确实新建了点赞记录」时通知——重复点赞（幂等命中）不重复打扰作者
            notificationPort.notify(post.getAuthorId(), userId,
                    new NotificationMessage(NotificationType.LIKE, "有人赞了你的帖子", null, "/posts/" + postId));
        }
        return likeState(postId, userId);
    }

    /** 取消点赞，幂等 */
    @Transactional
    public LikeStateResponse unlike(Long postId, Long userId) {
        userService.requireUsableById(userId);
        requireVisiblePost(postId, userId);
        likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, postId, userId)
                .ifPresent(likeRepository::delete);
        return likeState(postId, userId);
    }

    // ---------------------------------------------------------------- 内部方法

    /**
     * 封禁/黑名单已在 {@link UserService#requireUsableById} 统一拦截，这里只看发布门槛。
     */
    private void assertCanPost(User author) {
        if (author.getCreditScore() < postProperties.minCreditToPost()) {
            throw new BizException(ErrorCode.CREDIT_TOO_LOW);
        }
    }

    /**
     * 本地硬拦截（P2）：正文与联系方式分别过一遍。
     *
     * <p>联系方式必须一起校验——它同样是用户输入、同样对认证用户可见，
     * 「正文干净、联系方式里夹带」是显然的绕过路径。</p>
     */
    private void requireAllowedContent(String content, String contact) {
        contentGuard.check(content);
        contentGuard.check(contact);
    }

    private Topic requireEnabledTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .filter(Topic::isEnabled)
                .orElseThrow(() -> new BizException(ErrorCode.TOPIC_NOT_FOUND));
    }

    private Post requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    /** 不可见的帖子（待审/驳回/下架且非本人）一律按「不存在」返回，不泄露存在性 */
    private Post requireVisiblePost(Long postId, Long viewerId) {
        Post post = requirePost(postId);
        if (!post.canBeViewedBy(viewerId)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    /**
     * 不变量 #2：查看他人联系方式必须留痕，便于被人恶意批量抓取时追溯。
     * 看自己的帖子不记，否则日志会被作者自己的浏览刷屏。
     *
     * <p>审计写入按用户维度限流（{@link #CONTACT_VIEW_MAX_PER_WINDOW} / {@link #CONTACT_VIEW_RATE_WINDOW}），
     * 超限后<b>不再写审计行但仍正常返回详情</b>——限流是防「GET 带写放大」刷爆 {@code review_log}，
     * 绝不能因此影响阅读体验（因此这里不抛异常、不返回 4xx）。</p>
     */
    private void recordContactView(Post post, Viewer viewer) {
        if (!viewer.authenticated() || viewer.is(post.getAuthorId())) {
            return;
        }
        if (!post.contactVisibleTo(viewer.userId(), true) || post.getContact() == null) {
            return;
        }
        // 限流放在「确要写审计」之后：看自己的帖子 / 未认证 / 联系方式不可见都不消耗额度
        long views = ephemeralStore.increment(
                CONTACT_VIEW_RATE_KEY_PREFIX + viewer.userId(), CONTACT_VIEW_RATE_WINDOW);
        if (views > CONTACT_VIEW_MAX_PER_WINDOW) {
            // 日志只带 userId 与丢弃计数，不得打印任何身份明文（学号/邮箱）或联系方式
            log.warn("CONTACT_VIEW 审计写入超限，已丢弃 userId={} 本窗口累计丢弃={}",
                    viewer.userId(), views - CONTACT_VIEW_MAX_PER_WINDOW);
            return;
        }
        auditLogService.record(AuditTargetType.POST, post.getId(), AuditAction.CONTACT_VIEW, viewer.userId(), null);
    }

    private String normalizeContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return null;
        }
        String trimmed = contact.trim();
        if (trimmed.length() > MAX_CONTACT_LENGTH) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        return trimmed;
    }

    /**
     * 图片地址校验（P2 起升级为归属绑定，T-12'）：格式 → 存在 → 属本人 → 未被引用。
     *
     * <p>格式校验挡住 {@code javascript:} / 外链 / 伪造的 {@code /uploads} 路径（XSS 与盗链）；
     * 归属校验挡住「复制他人图片 URL 直接发帖」；绑定校验挡住「一图多帖」。返回登记行实体，
     * 发帖落库后据此回填 {@code bound_post_id}，避免重复查库。</p>
     */
    private ValidatedImages validateImages(List<String> images, Long authorId) {
        if (images == null || images.isEmpty()) {
            return new ValidatedImages(List.of(), List.of());
        }
        if (images.size() > postProperties.maxImagesPerPost()) {
            throw new BizException(ErrorCode.UPLOAD_INVALID);
        }
        Set<String> seen = new HashSet<>();
        List<String> urls = new ArrayList<>(images.size());
        List<UploadedFile> files = new ArrayList<>(images.size());
        for (String url : images) {
            String objectKey = StorageUrl.objectKey(url)
                    .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_INVALID));
            // 同帖内重复引用同一对象也按「已被使用」拒绝：一张图不能在一条帖子里占两个位置
            if (!seen.add(objectKey)) {
                throw new BizException(ErrorCode.UPLOAD_ALREADY_BOUND);
            }
            UploadedFile file = uploadedFileRepository.findByObjectKey(objectKey)
                    .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_NOT_FOUND));
            if (!file.isUploadedBy(authorId)) {
                throw new BizException(ErrorCode.UPLOAD_NOT_OWNED);
            }
            if (file.isBound()) {
                throw new BizException(ErrorCode.UPLOAD_ALREADY_BOUND);
            }
            // 归一为干净 URL（去掉可能的签名查询串）：落库与回显都只认原地址
            urls.add(StorageUrl.publicUrl(objectKey));
            files.add(file);
        }
        return new ValidatedImages(List.copyOf(urls), List.copyOf(files));
    }

    /**
     * 落 {@code post_image} 行，并把登记行标记为已绑定。
     *
     * <p>两件事同一事务完成：否则会出现「图片已进帖子、登记却仍显示未绑定」的中间态，
     * 让同一对象被另一个帖子再次引用（一图多帖）。URL 由 objectKey 重建，保证与对外形态一致。</p>
     */
    private void saveImages(Long postId, List<UploadedFile> files) {
        if (files.isEmpty()) {
            return;
        }
        List<PostImage> images = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            UploadedFile file = files.get(index);
            images.add(PostImage.of(postId, StorageUrl.publicUrl(file.getObjectKey()), index));
            file.bindTo(postId);
        }
        postImageRepository.saveAll(images);
    }

    /** 校验通过的图片：对外地址（原样返回给前端）+ 对应登记行（发帖后回填绑定） */
    private record ValidatedImages(List<String> urls, List<UploadedFile> files) {
    }

    private Map<Long, List<String>> loadImageUrls(Collection<Long> postIds) {
        return postImageRepository.findByPostIdInOrderBySortOrderAsc(postIds).stream()
                .collect(Collectors.groupingBy(
                        PostImage::getPostId,
                        Collectors.mapping(PostImage::getUrl, Collectors.toList())));
    }

    private Map<Long, Long> loadLikeCounts(LikeTargetType targetType, Collection<Long> targetIds) {
        return likeRepository.countByTargets(targetType, targetIds).stream()
                .collect(Collectors.toMap(LikeCount::targetId, LikeCount::count));
    }

    private Set<Long> loadLikedIds(LikeTargetType targetType, Collection<Long> targetIds, Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return Set.copyOf(likeRepository.findLikedTargetIds(targetType, userId, targetIds));
    }

    private Map<Long, String> loadTopicNames(List<Post> posts) {
        Set<Long> topicIds = posts.stream().map(Post::getTopicId).collect(Collectors.toSet());
        return topicRepository.findAllById(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName));
    }

    private LikeStateResponse likeState(Long postId, Long userId) {
        long count = likeRepository.countByTargetTypeAndTargetId(LikeTargetType.POST, postId);
        boolean liked = likeRepository
                .findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, postId, userId).isPresent();
        return new LikeStateResponse(count, liked);
    }

    private PostDetailResponse toDetail(Post post, String topicName, String topicRules, List<String> images,
                                        long likeCount, boolean liked, Viewer viewer) {
        boolean contactVisible = post.contactVisibleTo(viewer.userId(), viewer.authenticated());
        boolean mine = viewer.is(post.getAuthorId());
        return new PostDetailResponse(
                post.getId(),
                post.getTopicId(),
                topicName,
                topicRules,
                post.getContent(),
                contactVisible ? post.getContact() : null,
                contactVisible,
                images,
                post.getAnonymousCode(),
                likeCount,
                liked,
                post.getCommentCount(),
                mine,
                mine ? post.getReviewReason() : null,
                post.getStatus() == PostStatus.TAKEN_DOWN,
                post.getCreatedAt());
    }

    private long parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            // 首页游标取 Long.MAX_VALUE，省掉 SQL 里的 null 分支
            return Long.MAX_VALUE;
        }
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

    /**
     * 解析热门游标「分数(微单位):帖子 id」。缺段 / 非数字 / id 非正一律按非法游标拒绝。
     * 分数段允许为 0（冷启动帖子分数可接近 0）。
     */
    private HotCursor parseHotCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return HotCursor.firstPage();
        }
        String[] parts = raw.split(KEY_SEPARATOR);
        if (parts.length != CURSOR_SEGMENTS) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
        try {
            long scoreMicros = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (scoreMicros < 0 || id <= 0) {
                throw new NumberFormatException("游标各段不合法");
            }
            return new HotCursor(scoreMicros, id);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatHotCursor(ScoredPost last) {
        return last.scoreMicros() + KEY_SEPARATOR + last.post().getId();
    }

    /** 解析「我的帖子」游标「发布时间(微秒):帖子 id」 */
    private MineCursor parseMineCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new MineCursor(null, null);
        }
        String[] parts = raw.split(KEY_SEPARATOR);
        if (parts.length != CURSOR_SEGMENTS) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
        try {
            long micros = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (micros <= 0 || id <= 0) {
                throw new NumberFormatException("游标各段不合法");
            }
            return new MineCursor(CursorCodec.decode(micros), id);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatMineCursor(Post last) {
        return CursorCodec.encode(last.getCreatedAt()) + KEY_SEPARATOR + last.getId();
    }

    /** 复合游标段分隔符（信息流系）/ 段数（均为两段） */
    private static final String KEY_SEPARATOR = ":";
    private static final int CURSOR_SEGMENTS = 2;

    /** 一条带热度分的候选帖子 */
    private record ScoredPost(Post post, long scoreMicros) {

        /** 排序为「分数降序、id 降序」，故「在本游标之后」= 分数更小，或分数相同且 id 更小 */
        boolean isAfter(HotCursor cursor) {
            return scoreMicros < cursor.scoreMicros()
                    || (scoreMicros == cursor.scoreMicros() && post.getId() < cursor.id());
        }
    }

    /** 热门游标：{@code (分数, id)} 复合键；首页用 {@code first=true} 标识 */
    private record HotCursor(long scoreMicros, long id, boolean first) {

        static HotCursor firstPage() {
            return new HotCursor(0L, 0L, true);
        }

        HotCursor(long scoreMicros, long id) {
            this(scoreMicros, id, false);
        }
    }

    /** 「我的帖子」游标：与仓储排序键 {@code (created_at desc, id desc)} 逐一对应 */
    private record MineCursor(Instant createdAt, Long id) {
    }
}
