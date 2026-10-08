package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.common.web.TextPreview;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostImage;
import com.qinglan.post.repository.PostImageRepository;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.ReviewQueueProposalResponse;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewProposal;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.ReviewVote;
import com.qinglan.review.entity.VoteDecision;
import com.qinglan.review.entity.VoteTally;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.review.repository.ReviewQueueRow;
import com.qinglan.review.repository.ReviewVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审核队列：按举报目标聚合，供管理端工作台逐条处置。
 *
 * <p>排序与分页都在数据库完成（举报数降序、首报时间升序），内存只做装配。一页之内用若干次
 * 批量查询补齐摘要、理由、提案进度与帖子图片，杜绝逐行查询造成的 N+1。图片是审核的必要输入：
 * 只给正文摘要时，管理员根本看不到随帖的图，等于漏审。</p>
 *
 * <p>队列里不出现举报人的任何信息——举报人身份不出审核队列（不变量 #1 的引申）。</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int PREVIEW_LENGTH = 80;
    private static final String KEY_SEPARATOR = ":";

    /**
     * 首页哨兵：游标各段取极值后，仓储的键集条件里第一个比较恒真，
     * 因此首页与后续页共用同一条查询，不需要维护两份近乎相同的 JPQL。
     */
    private static final long FIRST_PAGE_REPORT_COUNT = Long.MAX_VALUE;
    private static final Instant FIRST_PAGE_FIRST_REPORTED_AT = Instant.EPOCH;
    private static final long FIRST_PAGE_TARGET_ID = Long.MAX_VALUE;
    private static final int FIRST_PAGE_TYPE_RANK = Integer.MAX_VALUE;
    /** 游标段数：举报数 : 首报微秒 : 目标 id : 目标类型序 */
    private static final int CURSOR_SEGMENTS = 4;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final CommentRepository commentRepository;
    private final ReviewProposalRepository proposalRepository;
    private final ReviewVoteRepository voteRepository;

    /**
     * @param viewerId 当前操作者，用于回填「我是否已投这一票」；队列行只给汇总，不含任何投票人身份
     */
    @Transactional(readOnly = true)
    public CursorPage<ReviewQueueItemResponse> queue(String cursorRaw, Long viewerId) {
        ReviewQueueCursor cursor = parseCursor(cursorRaw);
        // 多取一条判断是否还有下一页，避免额外 count 查询
        List<ReviewQueueRow> rows = reportRepository.findQueueAggregates(
                ReportStatus.PENDING,
                cursor.reportCount(),
                cursor.firstReportedAt(),
                cursor.targetId(),
                cursor.typeRank(),
                PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = rows.size() > DEFAULT_PAGE_SIZE;
        List<ReviewQueueRow> page = hasMore ? List.copyOf(rows.subList(0, DEFAULT_PAGE_SIZE)) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        Map<Long, Post> posts = loadPosts(page);
        Map<Long, List<String>> postImages = loadPostImages(page);
        Map<Long, Comment> comments = loadComments(page);
        Map<String, ReportSummary> summaries = loadReportSummaries(page);
        Map<String, ReviewQueueProposalResponse> proposals = loadOpenProposals(page, viewerId);

        List<ReviewQueueItemResponse> items = page.stream()
                .map(row -> toItem(row, posts, postImages, comments, summaries, proposals))
                .toList();
        String nextCursor = hasMore ? formatCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    // ---------------------------------------------------------------- 内部方法

    private Map<Long, Post> loadPosts(List<ReviewQueueRow> page) {
        List<Long> ids = targetIds(page, ReviewTargetType.POST);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return postRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Post::getId, post -> post));
    }

    /**
     * 一页之内按 POST 目标批量取图：一次 {@code findByPostIdInOrderBySortOrderAsc} 覆盖整页，
     * 杜绝逐行查询造成的 N+1。评论目标没有图片，故只取 POST；仓储已按 {@code sort_order} 升序返回，
     * 分组用有序 Map 保序。未在映射里的 POST（已被删除）在装配时取空列表。
     */
    private Map<Long, List<String>> loadPostImages(List<ReviewQueueRow> page) {
        List<Long> ids = targetIds(page, ReviewTargetType.POST);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> images = new LinkedHashMap<>();
        for (PostImage image : postImageRepository.findByPostIdInOrderBySortOrderAsc(ids)) {
            images.computeIfAbsent(image.getPostId(), ignored -> new ArrayList<>()).add(image.getUrl());
        }
        return images;
    }

    private Map<Long, Comment> loadComments(List<ReviewQueueRow> page) {
        List<Long> ids = targetIds(page, ReviewTargetType.COMMENT);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Comment::getId, comment -> comment));
    }

    /**
     * 一页之内按目标装配举报摘要：理由码与补充说明都去重（同一目标可能被多人用同一理由举报），
     * 各自用有序集合保序。两次批量查询覆盖帖子与评论两类目标，无逐行查询。
     */
    private Map<String, ReportSummary> loadReportSummaries(List<ReviewQueueRow> page) {
        Map<String, Set<ReportReason>> reasons = new HashMap<>();
        Map<String, Set<String>> details = new HashMap<>();
        collect(ReviewTargetType.POST, targetIds(page, ReviewTargetType.POST), reasons, details);
        collect(ReviewTargetType.COMMENT, targetIds(page, ReviewTargetType.COMMENT), reasons, details);

        Map<String, ReportSummary> summaries = new HashMap<>();
        reasons.forEach((target, values) -> summaries.put(target,
                new ReportSummary(List.copyOf(values), List.copyOf(details.getOrDefault(target, Set.of())))));
        return summaries;
    }

    private void collect(ReviewTargetType targetType, List<Long> targetIds,
                         Map<String, Set<ReportReason>> reasonSink, Map<String, Set<String>> detailSink) {
        if (targetIds.isEmpty()) {
            return;
        }
        List<Report> reports = reportRepository
                .findByTargetTypeAndTargetIdInAndStatus(targetType, targetIds, ReportStatus.PENDING);
        for (Report report : reports) {
            String target = key(targetType, report.getTargetId());
            reasonSink.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(report.getReasonCode());
            String detail = report.getDetail();
            if (detail != null && !detail.isBlank()) {
                detailSink.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(detail);
            }
        }
    }

    /**
     * 一页之内按目标装配「正在进行的处置提案」（方案 9.2）。两次批量查询覆盖帖子与评论两类目标，
     * 无逐行查询。同目标最多一条提案（唯一键保证），因此按目标建映射不会互相覆盖。
     */
    private Map<String, ReviewQueueProposalResponse> loadOpenProposals(List<ReviewQueueRow> page, Long viewerId) {
        Map<String, ReviewQueueProposalResponse> proposals = new HashMap<>();
        collectOpenProposals(ReviewTargetType.POST, targetIds(page, ReviewTargetType.POST), viewerId, proposals);
        collectOpenProposals(ReviewTargetType.COMMENT, targetIds(page, ReviewTargetType.COMMENT), viewerId, proposals);
        return proposals;
    }

    private void collectOpenProposals(ReviewTargetType targetType, List<Long> targetIds, Long viewerId,
                                      Map<String, ReviewQueueProposalResponse> sink) {
        if (targetIds.isEmpty()) {
            return;
        }
        List<ReviewProposal> proposals = proposalRepository.findOpenByTargets(targetType, targetIds);
        if (proposals.isEmpty()) {
            return;
        }
        List<Long> proposalIds = proposals.stream().map(ReviewProposal::getId).toList();
        Map<Long, List<ReviewVote>> votesByProposal = new HashMap<>();
        for (ReviewVote vote : voteRepository.findByProposalIdIn(proposalIds)) {
            votesByProposal.computeIfAbsent(vote.getProposalId(), ignored -> new ArrayList<>()).add(vote);
        }
        for (ReviewProposal proposal : proposals) {
            List<ReviewVote> votes = votesByProposal.getOrDefault(proposal.getId(), List.of());
            int agree = (int) votes.stream().filter(vote -> vote.getDecision() == VoteDecision.AGREE).count();
            VoteTally tally = VoteTally.of(proposal.getSnapshotSize(), agree, votes.size() - agree);
            VoteDecision mine = viewerId == null ? null : votes.stream()
                    .filter(vote -> vote.getVoterId().equals(viewerId))
                    .map(ReviewVote::getDecision)
                    .findFirst()
                    .orElse(null);
            sink.put(key(targetType, proposal.getTargetId()), new ReviewQueueProposalResponse(
                    proposal.getId(), proposal.getAction(), proposal.getStatus(),
                    agree, tally.neededCount(), tally.votedCount(), mine));
        }
    }

    private ReviewQueueItemResponse toItem(ReviewQueueRow row, Map<Long, Post> posts,
                                           Map<Long, List<String>> postImages,
                                           Map<Long, Comment> comments,
                                           Map<String, ReportSummary> summaries,
                                           Map<String, ReviewQueueProposalResponse> proposals) {
        ReportSummary summary = summaries.getOrDefault(key(row.targetType(), row.targetId()), ReportSummary.EMPTY);
        ReviewQueueProposalResponse proposal = proposals.get(key(row.targetType(), row.targetId()));
        return switch (row.targetType()) {
            case POST -> {
                Post post = posts.get(row.targetId());
                yield new ReviewQueueItemResponse(
                        ReviewTargetType.POST,
                        row.targetId(),
                        preview(post == null ? null : post.getContent()),
                        postImages.getOrDefault(row.targetId(), List.of()),
                        post == null ? null : post.getStatus().name(),
                        (int) row.reportCount(),
                        row.firstReportedAt(),
                        summary.reasons(),
                        summary.details(),
                        proposal);
            }
            case COMMENT -> {
                Comment comment = comments.get(row.targetId());
                yield new ReviewQueueItemResponse(
                        ReviewTargetType.COMMENT,
                        row.targetId(),
                        preview(comment == null ? null : comment.getContent()),
                        // 评论没有图片：返回空列表而非 null，前端无需判空
                        List.of(),
                        comment == null ? null : comment.getStatus().name(),
                        (int) row.reportCount(),
                        row.firstReportedAt(),
                        summary.reasons(),
                        summary.details(),
                        proposal);
            }
        };
    }

    /** 一个目标的举报摘要：去重的理由码 + 去重的补充说明（举报人选「其他」时填写的说明） */
    private record ReportSummary(List<ReportReason> reasons, List<String> details) {
        private static final ReportSummary EMPTY = new ReportSummary(List.of(), List.of());
    }

    private List<Long> targetIds(List<ReviewQueueRow> page, ReviewTargetType targetType) {
        return page.stream()
                .filter(row -> row.targetType() == targetType)
                .map(ReviewQueueRow::targetId)
                .toList();
    }

    private String preview(String content) {
        return TextPreview.of(content, PREVIEW_LENGTH);
    }

    private String key(ReviewTargetType targetType, Long targetId) {
        return targetType + KEY_SEPARATOR + targetId;
    }

    /**
     * 解析游标。游标对客户端不透明，只要求原样回传；格式为
     * 「举报数:首报微秒:目标 id:目标类型序」，四段必须齐全，数字段不得为负，
     * 缺段或分段错位一律按非法游标拒绝。
     */
    private ReviewQueueCursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ReviewQueueCursor(FIRST_PAGE_REPORT_COUNT, FIRST_PAGE_FIRST_REPORTED_AT,
                    FIRST_PAGE_TARGET_ID, FIRST_PAGE_TYPE_RANK);
        }
        String[] parts = raw.split(KEY_SEPARATOR);
        if (parts.length != CURSOR_SEGMENTS) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
        try {
            long reportCount = Long.parseLong(parts[0]);
            Instant firstReportedAt = CursorCodec.decode(Long.parseLong(parts[1]));
            long targetId = Long.parseLong(parts[2]);
            int typeRank = Integer.parseInt(parts[3]);
            if (reportCount <= 0 || targetId <= 0 || typeRank < 0) {
                throw new NumberFormatException("游标各段不合法");
            }
            return new ReviewQueueCursor(reportCount, firstReportedAt, targetId, typeRank);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatCursor(ReviewQueueRow row) {
        return row.reportCount() + KEY_SEPARATOR + CursorCodec.encode(row.firstReportedAt())
                + KEY_SEPARATOR + row.targetId() + KEY_SEPARATOR + typeRank(row.targetType());
    }

    /**
     * 目标类型序：必须与 {@link ReportRepository#findQueueAggregates} 里的 {@code case} 表达式一致。
     * 新增目标类型时两处都要补档，否则同 id 并列处会漏行或重复。
     */
    private int typeRank(ReviewTargetType targetType) {
        return targetType == ReviewTargetType.POST ? 0 : 1;
    }

    /** 队列游标：与 {@link ReportRepository#findQueueAggregates} 的排序键逐一对应 */
    private record ReviewQueueCursor(long reportCount, Instant firstReportedAt, long targetId, int typeRank) {
    }
}
