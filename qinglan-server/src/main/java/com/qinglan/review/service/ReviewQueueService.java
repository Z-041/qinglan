package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.entity.Post;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewQueueRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审核队列：按举报目标聚合，供管理端工作台逐条处置。
 *
 * <p>排序与分页都在数据库完成（举报数降序、首报时间升序），内存只做装配。一页之内用
 * 三次批量查询补齐摘要与理由，杜绝逐行查询造成的 N+1。</p>
 *
 * <p>队列里不出现举报人的任何信息——举报人身份不出审核队列（不变量 #1 的引申）。</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int PREVIEW_LENGTH = 80;
    private static final String ELLIPSIS = "...";
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
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public CursorPage<ReviewQueueItemResponse> queue(String cursorRaw) {
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
        Map<Long, Comment> comments = loadComments(page);
        Map<String, ReportSummary> summaries = loadReportSummaries(page);

        List<ReviewQueueItemResponse> items = page.stream()
                .map(row -> toItem(row, posts, comments, summaries))
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

    private ReviewQueueItemResponse toItem(ReviewQueueRow row, Map<Long, Post> posts,
                                           Map<Long, Comment> comments,
                                           Map<String, ReportSummary> summaries) {
        ReportSummary summary = summaries.getOrDefault(key(row.targetType(), row.targetId()), ReportSummary.EMPTY);
        return switch (row.targetType()) {
            case POST -> {
                Post post = posts.get(row.targetId());
                yield new ReviewQueueItemResponse(
                        ReviewTargetType.POST,
                        row.targetId(),
                        preview(post == null ? null : post.getContent()),
                        post == null ? null : post.getStatus().name(),
                        (int) row.reportCount(),
                        row.firstReportedAt(),
                        summary.reasons(),
                        summary.details());
            }
            case COMMENT -> {
                Comment comment = comments.get(row.targetId());
                yield new ReviewQueueItemResponse(
                        ReviewTargetType.COMMENT,
                        row.targetId(),
                        preview(comment == null ? null : comment.getContent()),
                        comment == null ? null : comment.getStatus().name(),
                        (int) row.reportCount(),
                        row.firstReportedAt(),
                        summary.reasons(),
                        summary.details());
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
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + ELLIPSIS;
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
