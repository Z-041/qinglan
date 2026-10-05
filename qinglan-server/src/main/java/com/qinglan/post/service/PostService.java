package com.qinglan.post.service;

import com.qinglan.common.anonymous.AnonymousCodeService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.interaction.LikeCount;
import com.qinglan.common.interaction.LikeRecord;
import com.qinglan.common.interaction.LikeRepository;
import com.qinglan.common.interaction.LikeStateResponse;
import com.qinglan.common.interaction.LikeTargetType;
import com.qinglan.common.security.Viewer;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.config.PostProperties;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.post.dto.PostDetailResponse;
import com.qinglan.post.dto.PostSummaryResponse;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostImage;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.entity.RiskLevel;
import com.qinglan.post.entity.Topic;
import com.qinglan.post.repository.PostImageRepository;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.TopicRepository;
import com.qinglan.review.entity.AuditAction;
import com.qinglan.review.entity.AuditTargetType;
import com.qinglan.review.service.AuditLogService;
import com.qinglan.user.entity.User;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子主流程：发布、信息流、详情、点赞、最小审核放行。
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
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_CONTACT_LENGTH = 200;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final TopicRepository topicRepository;
    private final LikeRepository likeRepository;
    private final AnonymousCodeService anonymousCodeService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final PostProperties postProperties;

    /**
     * 发布。落 Pending 而非直接可见——主帖先审后发，是设计方案第一章的红线 2。
     */
    @Transactional
    public PostDetailResponse create(Long authorId, CreatePostRequest request) {
        User author = userService.requireById(authorId);
        assertCanPost(author);
        Topic topic = requireEnabledTopic(request.topicId());

        String contact = normalizeContact(request.contact());
        if (topic.isRequiresContact() && contact == null) {
            throw new BizException(ErrorCode.CONTACT_REQUIRED);
        }
        List<String> images = validateImageUrls(request.images());

        Post post = Post.draft(authorId, topic.getId(), request.content().trim(), contact);
        post.submit(RiskLevel.LOW);
        Post saved = postRepository.save(post);
        // 代号依赖 post_id，只能在 INSERT 拿到主键之后回填（不变量 #1）
        saved.assignAnonymousCode(
                anonymousCodeService.deriveCode(saved.getId(), authorId, author.getAnonymousSalt()));
        saveImages(saved.getId(), images);

        log.info("用户发布帖子 postId={} topicId={}", saved.getId(), topic.getId());
        return toDetail(saved, topic.getName(), topic.getRules(), images, 0L, false, new Viewer(authorId));
    }

    /**
     * 信息流。只出审核通过的帖子，游标按 id 倒序翻页。
     */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryResponse> listFeed(Long topicId, String cursorRaw, Integer size, Viewer viewer) {
        int limit = normalizePageSize(size);
        long cursor = parseCursor(cursorRaw);
        // 多取一条用于判断是否还有下一页，避免额外 count 查询
        Pageable pageable = PageRequest.of(0, limit + 1);
        List<Post> found = topicId == null
                ? postRepository.findFeed(PostStatus.APPROVED, cursor, pageable)
                : postRepository.findFeedByTopic(PostStatus.APPROVED, topicId, cursor, pageable);

        boolean hasMore = found.size() > limit;
        List<Post> page = hasMore ? List.copyOf(found.subList(0, limit)) : found;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        // 四个批量查询装配完一页，杜绝逐帖查询造成的 N+1
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Map<Long, List<String>> imagesByPost = loadImageUrls(postIds);
        Map<Long, Long> likeCounts = loadLikeCounts(LikeTargetType.POST, postIds);
        Set<Long> likedIds = loadLikedIds(LikeTargetType.POST, postIds, viewer.userId());
        Map<Long, String> topicNames = loadTopicNames(page);

        List<PostSummaryResponse> items = page.stream()
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
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    /**
     * 详情。需要写审计日志（查看联系方式），故不是只读事务。
     */
    @Transactional
    public PostDetailResponse detail(Long postId, Viewer viewer) {
        Post post = requireVisiblePost(postId, viewer.userId());
        Topic topic = topicRepository.findById(post.getTopicId()).orElse(null);
        List<String> images = loadImageUrls(List.of(postId)).getOrDefault(postId, List.of());
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
        requireVisiblePost(postId, userId);
        if (likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, postId, userId).isEmpty()) {
            // uk_like_target_user 是并发下的最终防线：重复插入会被数据库拒绝，不会产生第二条
            likeRepository.save(LikeRecord.of(LikeTargetType.POST, postId, userId));
        }
        return likeState(postId, userId);
    }

    /** 取消点赞，幂等 */
    @Transactional
    public LikeStateResponse unlike(Long postId, Long userId) {
        requireVisiblePost(postId, userId);
        likeRepository.findByTargetTypeAndTargetIdAndUserId(LikeTargetType.POST, postId, userId)
                .ifPresent(likeRepository::delete);
        return likeState(postId, userId);
    }

    /**
     * 放行：Pending → Reviewing → Approved。
     *
     * <p>P1 还没有独立的认领队列，但状态机禁止跳态（不变量 #5），所以这里在同一事务内
     * 原子地走完两步，而不是把 Pending 直接写成 Approved。P2 接入机审与人工队列后，
     * 这两步会分别由不同角色触发。</p>
     */
    @Transactional
    public void approve(Long postId, Long operatorId) {
        Post post = requirePost(postId);
        Instant now = Instant.now();
        post.transitionTo(new ReviewState.Reviewing(operatorId, now));
        post.transitionTo(new ReviewState.Approved(operatorId, now));
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_APPROVE, operatorId, null);
        log.info("放行帖子 postId={}，操作人 operatorId={}", postId, operatorId);
    }

    /** 驳回：Reviewing → Rejected，理由对作者可见（同样先经 Reviewing，不跳态） */
    @Transactional
    public void reject(Long postId, Long operatorId, String reason) {
        Post post = requirePost(postId);
        post.transitionTo(new ReviewState.Reviewing(operatorId, Instant.now()));
        post.transitionTo(new ReviewState.Rejected("MANUAL_REJECT", reason));
        auditLogService.record(AuditTargetType.POST, postId, AuditAction.POST_REJECT, operatorId, reason);
        log.info("驳回帖子 postId={}，操作人 operatorId={}", postId, operatorId);
    }

    // ---------------------------------------------------------------- 内部方法

    private void assertCanPost(User author) {
        if (author.isBannedAt(Instant.now())) {
            throw new BizException(ErrorCode.USER_BANNED);
        }
        if (author.getCreditScore() < postProperties.minCreditToPost()) {
            throw new BizException(ErrorCode.CREDIT_TOO_LOW);
        }
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
     */
    private void recordContactView(Post post, Viewer viewer) {
        if (!viewer.authenticated() || viewer.is(post.getAuthorId())) {
            return;
        }
        if (!post.contactVisibleTo(viewer.userId(), true) || post.getContact() == null) {
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
     * 图片地址只接受本站上传产生的相对路径。
     *
     * <p>放任客户端传任意字符串的话，可以把 {@code javascript:} 或外链塞进 img 的 src，
     * 造成 XSS 与盗链。要求以 {@code /} 开头且不含协议分隔符，即可挡掉绝对 URL 与伪协议。</p>
     */
    private List<String> validateImageUrls(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        if (images.size() > postProperties.maxImagesPerPost()) {
            throw new BizException(ErrorCode.UPLOAD_INVALID);
        }
        for (String url : images) {
            if (url == null || !url.startsWith("/") || url.contains(":")) {
                throw new BizException(ErrorCode.UPLOAD_INVALID);
            }
        }
        return List.copyOf(images);
    }

    private void saveImages(Long postId, List<String> urls) {
        if (urls.isEmpty()) {
            return;
        }
        List<PostImage> images = new ArrayList<>(urls.size());
        for (int index = 0; index < urls.size(); index++) {
            images.add(PostImage.of(postId, urls.get(index), index));
        }
        postImageRepository.saveAll(images);
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

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        return size;
    }
}
