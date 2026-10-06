package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewQueueRow;
import com.qinglan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 审核队列的单元测试。
 *
 * <p>盯三件事：排序与分页键完全交给数据库（内存只装配）、队列里不出现举报人身份
 * （不变量 #1 的引申）、游标对客户端不透明且非法输入必须拒绝而不是静默从头开始。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    private static final Instant BASE = Instant.parse("2026-10-06T00:00:00Z");

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private ReviewQueueService reviewQueueService;

    @Test
    @DisplayName("没有待处理举报时返回空页，不产生多余的装配查询")
    void shouldReturnEmptyPageWhenQueueEmpty() {
        stubFirstPageRows(List.of());

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(postRepository, commentRepository);
    }

    @Test
    @DisplayName("帖子与评论混排：各自补齐摘要与状态，理由按目标去重")
    void shouldAssemblePostsAndCommentsInOnePage() {
        Instant secondAt = BASE.plusSeconds(60);
        stubFirstPageRows(List.of(
                new ReviewQueueRow(ReviewTargetType.POST, 1L, 3L, BASE),
                new ReviewQueueRow(ReviewTargetType.COMMENT, 55L, 1L, secondAt)));
        when(postRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(TestFixtures.approvedPost(1L, 7L, 3L, "wx-demo")));
        when(commentRepository.findAllById(List.of(55L)))
                .thenReturn(List.of(comment(55L, "评论内容")));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.POST, List.of(1L), ReportStatus.PENDING))
                .thenReturn(List.of(report(ReviewTargetType.POST, 1L, ReportReason.ABUSE),
                        report(ReviewTargetType.POST, 1L, ReportReason.ADVERTISEMENT),
                        report(ReviewTargetType.POST, 1L, ReportReason.ABUSE)));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.COMMENT, List.of(55L), ReportStatus.PENDING))
                .thenReturn(List.of(report(ReviewTargetType.COMMENT, 55L, ReportReason.PRIVACY)));

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null);

        assertThat(page.items()).hasSize(2);
        ReviewQueueItemResponse postItem = page.items().get(0);
        assertThat(postItem.targetType()).isEqualTo(ReviewTargetType.POST);
        assertThat(postItem.preview()).isEqualTo("测试正文");
        assertThat(postItem.targetStatus()).isEqualTo("APPROVED");
        assertThat(postItem.reportCount()).isEqualTo(3);
        assertThat(postItem.firstReportedAt()).isEqualTo(BASE);
        // 去重且保序：ABUSE 只出现一次
        assertThat(postItem.reasons()).containsExactly(ReportReason.ABUSE, ReportReason.ADVERTISEMENT);

        ReviewQueueItemResponse commentItem = page.items().get(1);
        assertThat(commentItem.targetStatus()).isEqualTo("PUBLISHED");
        assertThat(commentItem.reasons()).containsExactly(ReportReason.PRIVACY);
    }

    @Test
    @DisplayName("超长内容截断为 80 字加省略号，避免整段回吐给管理端")
    void shouldTruncateLongPreview() {
        stubFirstPageRows(List.of(new ReviewQueueRow(ReviewTargetType.COMMENT, 55L, 1L, BASE)));
        String longContent = "违".repeat(120);
        when(commentRepository.findAllById(List.of(55L)))
                .thenReturn(List.of(comment(55L, longContent)));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.COMMENT, List.of(55L), ReportStatus.PENDING)).thenReturn(List.of());

        String preview = reviewQueueService.queue(null).items().get(0).preview();

        assertThat(preview).hasSize(83).endsWith("...");
    }

    @Test
    @DisplayName("目标已被删除时该项仍返回，摘要与状态为空而不是整页失败")
    void shouldTolerateMissingTarget() {
        stubFirstPageRows(List.of(new ReviewQueueRow(ReviewTargetType.POST, 404L, 1L, BASE)));
        // 仓储默认返回空集合：目标已不存在

        ReviewQueueItemResponse item = reviewQueueService.queue(null).items().get(0);

        assertThat(item.targetId()).isEqualTo(404L);
        assertThat(item.preview()).isNull();
        assertThat(item.targetStatus()).isNull();
    }

    @Test
    @DisplayName("多取一条判断还有下一页，游标取本页最后一行且为透明字符串")
    void shouldEmitOpaqueCursorForNextPage() {
        Instant lastAt = BASE.plusSeconds(19);
        List<ReviewQueueRow> rows = IntStream.range(0, 21)
                .mapToObj(i -> new ReviewQueueRow(ReviewTargetType.POST, 100L - i, 1L, BASE.plusSeconds(i)))
                .toList();
        stubFirstPageRows(rows);

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null);

        assertThat(page.items()).hasSize(20);
        // 第 20 行（下标 19）的复合键：举报数:首报毫秒:目标 id:目标类型序（POST 为 0）
        assertThat(page.nextCursor()).isEqualTo("1:" + lastAt.getEpochSecond() * 1_000_000L + ":81:0");
    }

    @Test
    @DisplayName("合法游标原样解析后交给仓储，首页与后续页共用同一条查询")
    void shouldPassParsedCursorToRepository() {
        long micros = BASE.plusSeconds(5).getEpochSecond() * 1_000_000L;
        when(reportRepository.findQueueAggregates(eq(ReportStatus.PENDING), eq(2L),
                eq(BASE.plusSeconds(5)), eq(7L), eq(1), any(Pageable.class)))
                .thenReturn(List.of());

        reviewQueueService.queue("2:" + micros + ":7:1");

        verify(reportRepository).findQueueAggregates(eq(ReportStatus.PENDING), eq(2L),
                eq(BASE.plusSeconds(5)), eq(7L), eq(1), any(Pageable.class));
    }

    @Test
    @DisplayName("缺段、非数字、非正数的游标一律按非法游标拒绝，且不触碰仓储")
    void shouldRejectMalformedCursor() {
        assertThatThrownBy(() -> reviewQueueService.queue("not-a-cursor"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> reviewQueueService.queue("1:2"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        // 段数不足（少一段类型序）同样拒绝，避免把旧格式游标当成合法输入静默按首页重来
        assertThatThrownBy(() -> reviewQueueService.queue("1:" + BASE.toEpochMilli() + ":7"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> reviewQueueService.queue("1:" + BASE.toEpochMilli() + ":0:0"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> reviewQueueService.queue("1:" + BASE.toEpochMilli() + ":7:-1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);

        verifyNoInteractions(reportRepository, postRepository, commentRepository);
    }

    @Test
    @DisplayName("不变量 #1：队列响应不含举报人、作者身份等任何可关联字段")
    void shouldKeepReporterIdentityOutOfQueueItem() {
        Set<String> componentNames = Arrays.stream(ReviewQueueItemResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(componentNames).doesNotContain(
                "reporterId", "reporter", "authorId", "studentId", "anonymousCode", "email");
    }

    @Test
    @DisplayName("游标带微秒：边界行与下一行同毫秒时，续页仍按边界行的原始时刻")
    void shouldKeepMicrosWhenRowsShareTheSameMillisecond() {
        // 边界行 .000234s 与下一行 .000235s 落在同一毫秒：毫秒截断会让下一页重新取回边界行
        Instant boundary = BASE.plusSeconds(19).plusNanos(234_000);
        Instant next = BASE.plusSeconds(19).plusNanos(235_000);
        List<ReviewQueueRow> rows = IntStream.range(0, 21)
                .mapToObj(i -> new ReviewQueueRow(ReviewTargetType.POST, 100L - i, 1L,
                        i < 19 ? BASE.plusSeconds(i) : (i == 19 ? boundary : next)))
                .toList();
        stubFirstPageRows(rows);

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null);

        assertThat(rows.get(19).firstReportedAt().toEpochMilli())
                .isEqualTo(rows.get(20).firstReportedAt().toEpochMilli());
        assertThat(page.nextCursor())
                .isEqualTo("1:" + (boundary.getEpochSecond() * 1_000_000L + 234L) + ":81:0");

        reviewQueueService.queue(page.nextCursor());

        verify(reportRepository).findQueueAggregates(eq(ReportStatus.PENDING), eq(1L),
                eq(boundary), eq(81L), eq(0), any(Pageable.class));
    }

    // ------------------------------------------------------------------ 辅助

    private void stubFirstPageRows(List<ReviewQueueRow> rows) {
        when(reportRepository.findQueueAggregates(eq(ReportStatus.PENDING), anyLong(), any(Instant.class),
                anyLong(), anyInt(), any(Pageable.class))).thenReturn(rows);
    }

    private static Report report(ReviewTargetType targetType, long targetId, ReportReason reason) {
        return Report.submit(targetType, targetId, 900L, reason, null);
    }

    private static Comment comment(long id, String content) {
        Comment comment = Comment.create(1L, 7L, null, content);
        TestFixtures.setId(comment, id);
        return comment;
    }
}
