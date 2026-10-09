package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.storage.ImageUrlSigner;
import com.qinglan.common.storage.StorageProperties;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.entity.PostImage;
import com.qinglan.post.repository.PostImageRepository;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.UploadedFileRepository;
import com.qinglan.post.service.ImageAccessService;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.ReviewQueueProposalResponse;
import com.qinglan.review.dto.ReviewQueueQuery;
import com.qinglan.review.dto.ReviewQueueSort;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewProposal;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.ReviewVote;
import com.qinglan.review.entity.VoteDecision;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.review.repository.ReviewQueueRow;
import com.qinglan.review.repository.ReviewVoteRepository;
import com.qinglan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 审核队列的单元测试。
 *
 * <p>盯三件事：排序与分页键完全交给数据库（内存只装配）、队列里不出现举报人身份
 * （不变量 #1 的引申）、游标对客户端不透明且非法输入必须拒绝而不是静默从头开始。</p>
 *
 * <p>阶段 A 追加：筛选参数透传、只看超时的 24h 阈值、首报升序游标格式与仓储方法选择。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    private static final Instant BASE = Instant.parse("2026-10-06T00:00:00Z");
    /** 队列请求的操作者：用于回填「我是否已投这一票」 */
    private static final long VIEWER_ID = 42L;
    /** 无筛选、默认排序的条件（等价于改造前的行为） */
    private static final ReviewQueueQuery DEFAULT_QUERY = new ReviewQueueQuery(null, null, false, null);

    /** 合法对外地址（签名器只接受本服务生成的形态） */
    private static final String IMAGE_A = "/uploads/2026/10/" + "a".repeat(32) + ".jpg";
    private static final String IMAGE_B = "/uploads/2026/10/" + "b".repeat(32) + ".jpg";

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ReviewProposalRepository proposalRepository;

    @Mock
    private ReviewVoteRepository voteRepository;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    private ReviewQueueService reviewQueueService;

    @BeforeEach
    void setUp() {
        // 真实签名器：队列里的私有帖图必须真签，断言签名 URL 的形态才有意义（与 ImageUploadServiceTest 同法）
        ImageUrlSigner signer = new ImageUrlSigner(
                new StorageProperties(null, null, null, "unit-test-image-signing-secret", null),
                new MockEnvironment());
        // forPost 只用签名器，装配时不会触碰上传仓储
        ImageAccessService imageAccessService = new ImageAccessService(signer, uploadedFileRepository, postRepository);
        reviewQueueService = new ReviewQueueService(reportRepository, postRepository, postImageRepository,
                commentRepository, proposalRepository, voteRepository, imageAccessService);
    }

    @Test
    @DisplayName("没有待处理举报时返回空页，不产生多余的装配查询")
    void shouldReturnEmptyPageWhenQueueEmpty() {
        stubFirstPageRows(List.of());

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(postRepository, postImageRepository, commentRepository, proposalRepository,
                voteRepository);
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
                        reportWithDetail(ReviewTargetType.POST, 1L, ReportReason.OTHER, "疑似中介在拉群"),
                        reportWithDetail(ReviewTargetType.POST, 1L, ReportReason.OTHER, "疑似中介在拉群"),
                        report(ReviewTargetType.POST, 1L, ReportReason.ADVERTISEMENT),
                        report(ReviewTargetType.POST, 1L, ReportReason.ABUSE)));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.COMMENT, List.of(55L), ReportStatus.PENDING))
                .thenReturn(List.of(report(ReviewTargetType.COMMENT, 55L, ReportReason.PRIVACY)));

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID);

        assertThat(page.items()).hasSize(2);
        ReviewQueueItemResponse postItem = page.items().get(0);
        assertThat(postItem.targetType()).isEqualTo(ReviewTargetType.POST);
        assertThat(postItem.preview()).isEqualTo("测试正文");
        assertThat(postItem.targetStatus()).isEqualTo("APPROVED");
        assertThat(postItem.reportCount()).isEqualTo(3);
        assertThat(postItem.firstReportedAt()).isEqualTo(BASE);
        // 去重且保序：ABUSE 只出现一次
        assertThat(postItem.reasons())
                .containsExactly(ReportReason.ABUSE, ReportReason.OTHER, ReportReason.ADVERTISEMENT);
        // 补充说明同样去重后出参：重复的两条只留一条，管理端才读得到「其他」的说明
        assertThat(postItem.details()).containsExactly("疑似中介在拉群");
        // 该目标没有进行中提案时是 null（前端无需判空）
        assertThat(postItem.proposal()).isNull();
        // 该帖无图时是空列表，不是 null（前端无需判空）
        assertThat(postItem.images()).isEmpty();

        ReviewQueueItemResponse commentItem = page.items().get(1);
        assertThat(commentItem.targetStatus()).isEqualTo("PUBLISHED");
        assertThat(commentItem.reasons()).containsExactly(ReportReason.PRIVACY);
        // 没填说明时是空列表，不是 null（前端无需判空）
        assertThat(commentItem.details()).isEmpty();
        // 评论没有图片：同样是空列表
        assertThat(commentItem.images()).isEmpty();
    }

    @Test
    @DisplayName("POST 目标带图时按排序返回全部图片 URL，整页只批量查一次（杜绝 N+1）")
    void shouldReturnAllPostImagesInOrderWithSingleBatchQuery() {
        stubFirstPageRows(List.of(
                new ReviewQueueRow(ReviewTargetType.POST, 1L, 2L, BASE),
                new ReviewQueueRow(ReviewTargetType.POST, 2L, 1L, BASE.plusSeconds(1))));
        when(postRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                TestFixtures.approvedPost(1L, 7L, 3L, "wx-demo"),
                TestFixtures.approvedPost(2L, 8L, 3L, "wx-demo")));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.POST, List.of(1L, 2L), ReportStatus.PENDING)).thenReturn(List.of());
        when(postImageRepository.findByPostIdInOrderBySortOrderAsc(List.of(1L, 2L))).thenReturn(List.of(
                image(1L, "/uploads/a.jpg", 0),
                image(1L, "/uploads/b.jpg", 1),
                image(2L, "/uploads/c.jpg", 0)));

        List<ReviewQueueItemResponse> items = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID).items();

        assertThat(items.get(0).images()).containsExactly("/uploads/a.jpg", "/uploads/b.jpg");
        assertThat(items.get(1).images()).containsExactly("/uploads/c.jpg");
        // 整页一次批量查询，而不是逐行一次
        verify(postImageRepository, times(1)).findByPostIdInOrderBySortOrderAsc(anyList());
    }

    @Test
    @DisplayName("待审帖图签为短时 URL、已通过帖图保持干净：管理端 <img> 不带令牌也看得见图")
    void shouldSignPendingImagesButKeepApprovedClean() {
        stubFirstPageRows(List.of(
                new ReviewQueueRow(ReviewTargetType.POST, 1L, 2L, BASE),
                new ReviewQueueRow(ReviewTargetType.POST, 2L, 1L, BASE.plusSeconds(1))));
        when(postRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                TestFixtures.pendingPost(1L, 7L, 3L, "待审带图"),
                TestFixtures.approvedPost(2L, 8L, 3L, "wx-demo")));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.POST, List.of(1L, 2L), ReportStatus.PENDING)).thenReturn(List.of());
        when(postImageRepository.findByPostIdInOrderBySortOrderAsc(List.of(1L, 2L))).thenReturn(List.of(
                image(1L, IMAGE_A, 0),
                image(2L, IMAGE_B, 0)));

        List<ReviewQueueItemResponse> items = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID).items();

        // 待审（非 APPROVED）：签为短时签名 URL——管理端 <img> 不带令牌才取得到，否则破图漏审
        assertThat(items.get(0).targetStatus()).isEqualTo("PENDING");
        assertThat(items.get(0).images().get(0)).startsWith(IMAGE_A + "?").contains("exp=").contains("sig=");
        // 已通过：保持干净原 URL（可缓存、游客可直取），不得被签名污染
        assertThat(items.get(1).targetStatus()).isEqualTo("APPROVED");
        assertThat(items.get(1).images()).containsExactly(IMAGE_B);
    }

    @Test
    @DisplayName("COMMENT 目标没有图片：返回空列表而不是 null，且不触碰图片仓储")
    void shouldReturnEmptyImagesForCommentTarget() {
        stubFirstPageRows(List.of(new ReviewQueueRow(ReviewTargetType.COMMENT, 55L, 1L, BASE)));
        when(commentRepository.findAllById(List.of(55L)))
                .thenReturn(List.of(comment(55L, "评论内容")));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.COMMENT, List.of(55L), ReportStatus.PENDING)).thenReturn(List.of());

        ReviewQueueItemResponse item = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID).items().get(0);

        assertThat(item.images()).isEmpty();
        // 评论无图，不该为此去查图片表
        verifyNoInteractions(postImageRepository);
    }

    @Test
    @DisplayName("无图帖子返回空列表而不是 null")
    void shouldReturnEmptyImagesWhenPostHasNoImage() {
        stubFirstPageRows(List.of(new ReviewQueueRow(ReviewTargetType.POST, 1L, 1L, BASE)));
        when(postRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(TestFixtures.approvedPost(1L, 7L, 3L, "wx-demo")));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.POST, List.of(1L), ReportStatus.PENDING)).thenReturn(List.of());
        // 图片仓储默认返回空集合：该帖无图

        assertThat(reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID).items().get(0).images()).isEmpty();
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

        String preview = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID).items().get(0).preview();

        assertThat(preview).hasSize(83).endsWith("...");
    }

    @Test
    @DisplayName("目标已被删除时该项仍返回，摘要与状态为空而不是整页失败")
    void shouldTolerateMissingTarget() {
        stubFirstPageRows(List.of(new ReviewQueueRow(ReviewTargetType.POST, 404L, 1L, BASE)));
        // 仓储默认返回空集合：目标已不存在

        ReviewQueueItemResponse item = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID).items().get(0);

        assertThat(item.targetId()).isEqualTo(404L);
        assertThat(item.preview()).isNull();
        assertThat(item.targetStatus()).isNull();
    }

    @Test
    @DisplayName("多取一条判断还有下一页，默认排序游标是本页最后一行的四元组")
    void shouldEmitOpaqueCursorForNextPage() {
        Instant lastAt = BASE.plusSeconds(19);
        List<ReviewQueueRow> rows = IntStream.range(0, 21)
                .mapToObj(i -> new ReviewQueueRow(ReviewTargetType.POST, 100L - i, 1L, BASE.plusSeconds(i)))
                .toList();
        stubFirstPageRows(rows);

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID);

        assertThat(page.items()).hasSize(20);
        // 第 20 行（下标 19）的复合键：举报数:首报微秒:目标 id:目标类型序（POST 为 0）
        assertThat(page.nextCursor()).isEqualTo("1:" + lastAt.getEpochSecond() * 1_000_000L + ":81:0");
    }

    @Test
    @DisplayName("合法游标原样解析后交给仓储，首页与后续页共用同一条查询")
    void shouldPassParsedCursorToRepository() {
        long micros = BASE.plusSeconds(5).getEpochSecond() * 1_000_000L;
        when(reportRepository.findQueueAggregates(eq(ReportStatus.PENDING), anyList(), isNull(), isNull(),
                eq(2L), eq(BASE.plusSeconds(5)), eq(7L), eq(1), any(Pageable.class)))
                .thenReturn(List.of());

        reviewQueueService.queue("2:" + micros + ":7:1", DEFAULT_QUERY, VIEWER_ID);

        verify(reportRepository).findQueueAggregates(eq(ReportStatus.PENDING), anyList(), isNull(), isNull(),
                eq(2L), eq(BASE.plusSeconds(5)), eq(7L), eq(1), any(Pageable.class));
    }

    @Test
    @DisplayName("缺段、非数字、非正数的游标一律按非法游标拒绝，且不触碰仓储")
    void shouldRejectMalformedCursor() {
        assertThatThrownBy(() -> reviewQueueService.queue("not-a-cursor", DEFAULT_QUERY, VIEWER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> reviewQueueService.queue("1:2", DEFAULT_QUERY, VIEWER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        // 段数不足（少一段类型序）同样拒绝，避免把旧格式游标当成合法输入静默按首页重来
        assertThatThrownBy(() -> reviewQueueService.queue("1:" + BASE.toEpochMilli() + ":7", DEFAULT_QUERY, VIEWER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> reviewQueueService.queue("1:" + BASE.toEpochMilli() + ":0:0", DEFAULT_QUERY, VIEWER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> reviewQueueService.queue("1:" + BASE.toEpochMilli() + ":7:-1", DEFAULT_QUERY, VIEWER_ID))
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

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID);

        assertThat(rows.get(19).firstReportedAt().toEpochMilli())
                .isEqualTo(rows.get(20).firstReportedAt().toEpochMilli());
        assertThat(page.nextCursor())
                .isEqualTo("1:" + (boundary.getEpochSecond() * 1_000_000L + 234L) + ":81:0");

        reviewQueueService.queue(page.nextCursor(), DEFAULT_QUERY, VIEWER_ID);

        verify(reportRepository).findQueueAggregates(eq(ReportStatus.PENDING), anyList(), isNull(), isNull(),
                eq(1L), eq(boundary), eq(81L), eq(0), any(Pageable.class));
    }

    @Test
    @DisplayName("队列行带上该目标进行中的提案进度，让「点下架」的人不至于撞 409 后无处可去（方案 9.2）")
    void shouldAttachOpenProposalProgress() {
        stubFirstPageRows(List.of(new ReviewQueueRow(ReviewTargetType.POST, 1L, 2L, BASE)));
        when(postRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(TestFixtures.approvedPost(1L, 7L, 3L, "wx-demo")));
        when(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReviewTargetType.POST, List.of(1L), ReportStatus.PENDING)).thenReturn(List.of());

        ReviewProposal proposal = ReviewProposal.open(1L, ProposalAction.TAKE_DOWN, 900L,
                ReportReason.ABUSE, "人身攻击", 4);
        TestFixtures.setId(proposal, 12L);
        when(proposalRepository.findOpenByTargets(ReviewTargetType.POST, List.of(1L)))
                .thenReturn(List.of(proposal));
        when(voteRepository.findByProposalIdIn(List.of(12L))).thenReturn(List.of(
                ReviewVote.of(12L, VIEWER_ID, VoteDecision.AGREE),
                ReviewVote.of(12L, 900L, VoteDecision.DISAGREE)));

        ReviewQueueProposalResponse progress =
                reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID).items().get(0).proposal();

        assertThat(progress).isNotNull();
        assertThat(progress.proposalId()).isEqualTo(12L);
        assertThat(progress.action()).isEqualTo(ProposalAction.TAKE_DOWN);
        assertThat(progress.status()).isEqualTo(ProposalStatus.OPEN);
        assertThat(progress.agreeCount()).isEqualTo(1);
        // 4 人在册 → needed = 3；已投 2 票
        assertThat(progress.needed()).isEqualTo(3);
        assertThat(progress.votedCount()).isEqualTo(2);
        // 只回我自己那一票，不暴露别人投了什么（决策 D8）
        assertThat(progress.myDecision()).isEqualTo(VoteDecision.AGREE);
    }

    // ------------------------------------------------------------------ 阶段 A：筛选与排序

    @Test
    @DisplayName("筛选条件原样透传：理由集合 / 目标类型进仓储，未勾选「只看超时」时不传阈值")
    void shouldPassFiltersToRepository() {
        stubFirstPageRows(List.of());
        ReviewQueueQuery query = new ReviewQueueQuery(
                List.of(ReportReason.ABUSE, ReportReason.PRIVACY), ReviewTargetType.POST, false,
                ReviewQueueSort.REPORT_COUNT_DESC);

        reviewQueueService.queue(null, query, VIEWER_ID);

        verify(reportRepository).findQueueAggregates(eq(ReportStatus.PENDING),
                eq(List.of(ReportReason.ABUSE, ReportReason.PRIVACY)), eq(ReviewTargetType.POST), isNull(),
                anyLong(), any(Instant.class), anyLong(), anyInt(), any(Pageable.class));
    }

    @Test
    @DisplayName("不选理由时用全部理由填充（等价于不过滤），避免空集合 IN")
    void shouldFillAllReasonsWhenNoFilter() {
        stubFirstPageRows(List.of());

        reviewQueueService.queue(null, DEFAULT_QUERY, VIEWER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReportReason>> reasons = ArgumentCaptor.forClass(List.class);
        verify(reportRepository).findQueueAggregates(eq(ReportStatus.PENDING), reasons.capture(), isNull(),
                isNull(), anyLong(), any(Instant.class), anyLong(), anyInt(), any(Pageable.class));
        assertThat(reasons.getValue()).containsExactlyInAnyOrder(ReportReason.values());
    }

    @Test
    @DisplayName("只看超时：阈值是 now-24h（边界由仓储的 < 判定）")
    void shouldPassOverdueThresholdWhenOverdueOnly() {
        stubFirstPageRows(List.of());
        Instant before = Instant.now().minus(Duration.ofHours(24)).minusSeconds(5);
        ReviewQueueQuery query = new ReviewQueueQuery(null, null, true, null);

        reviewQueueService.queue(null, query, VIEWER_ID);

        ArgumentCaptor<Instant> overdueBefore = ArgumentCaptor.forClass(Instant.class);
        verify(reportRepository).findQueueAggregates(eq(ReportStatus.PENDING), anyList(), isNull(),
                overdueBefore.capture(), anyLong(), any(Instant.class), anyLong(), anyInt(), any(Pageable.class));
        assertThat(overdueBefore.getValue())
                .isAfter(before)
                .isBefore(Instant.now().minus(Duration.ofHours(24)).plusSeconds(5));
    }

    @Test
    @DisplayName("首报升序：走专用仓储方法，游标为三元组（首报微秒:目标 id:类型序）")
    void shouldUseFirstReportedSortRepositoryAndThreeSegmentCursor() {
        Instant lastAt = BASE.plusSeconds(19);
        List<ReviewQueueRow> rows = IntStream.range(0, 21)
                .mapToObj(i -> new ReviewQueueRow(ReviewTargetType.POST, 100L - i, 1L, BASE.plusSeconds(i)))
                .toList();
        when(reportRepository.findQueueAggregatesByFirstReportedAsc(eq(ReportStatus.PENDING), anyList(),
                isNull(), isNull(), any(Instant.class), anyLong(), anyInt(), any(Pageable.class)))
                .thenReturn(rows);
        ReviewQueueQuery query = new ReviewQueueQuery(null, null, false, ReviewQueueSort.FIRST_REPORTED_ASC);

        CursorPage<ReviewQueueItemResponse> page = reviewQueueService.queue(null, query, VIEWER_ID);

        assertThat(page.items()).hasSize(20);
        // 三元组：首报微秒:目标 id:类型序（不含举报数）
        assertThat(page.nextCursor()).isEqualTo(lastAt.getEpochSecond() * 1_000_000L + ":81:0");
        // 默认排序的方法不该被调用
        verify(reportRepository, times(0)).findQueueAggregates(any(), anyList(), any(), any(),
                anyLong(), any(Instant.class), anyLong(), anyInt(), any(Pageable.class));
    }

    @Test
    @DisplayName("首报升序的续页游标被正确解析并走专用仓储方法")
    void shouldParseFirstReportedCursor() {
        long micros = BASE.plusSeconds(5).getEpochSecond() * 1_000_000L;
        when(reportRepository.findQueueAggregatesByFirstReportedAsc(eq(ReportStatus.PENDING), anyList(),
                isNull(), isNull(), eq(BASE.plusSeconds(5)), eq(7L), eq(1), any(Pageable.class)))
                .thenReturn(List.of());
        ReviewQueueQuery query = new ReviewQueueQuery(null, null, false, ReviewQueueSort.FIRST_REPORTED_ASC);

        reviewQueueService.queue(micros + ":7:1", query, VIEWER_ID);

        verify(reportRepository).findQueueAggregatesByFirstReportedAsc(eq(ReportStatus.PENDING), anyList(),
                isNull(), isNull(), eq(BASE.plusSeconds(5)), eq(7L), eq(1), any(Pageable.class));
    }

    @Test
    @DisplayName("排序与游标段数不匹配（首报升序却传四元组）按非法游标拒绝")
    void shouldRejectCursorSegmentsMismatchingSort() {
        ReviewQueueQuery query = new ReviewQueueQuery(null, null, false, ReviewQueueSort.FIRST_REPORTED_ASC);

        assertThatThrownBy(() -> reviewQueueService.queue("1:2:3:4", query, VIEWER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        verifyNoInteractions(reportRepository);
    }

    // ------------------------------------------------------------------ 辅助

    /** 用 any() 匹配可选筛选（targetType / overdueBefore），使带筛选的调用也能命中同一桩 */
    private void stubFirstPageRows(List<ReviewQueueRow> rows) {
        when(reportRepository.findQueueAggregates(eq(ReportStatus.PENDING), anyList(), any(), any(),
                anyLong(), any(Instant.class), anyLong(), anyInt(), any(Pageable.class))).thenReturn(rows);
    }

    private static Report report(ReviewTargetType targetType, long targetId, ReportReason reason) {
        return Report.submit(targetType, targetId, 900L, reason, null);
    }

    /** 带补充说明的举报：理由选「其他」时说明是唯一线索，必须能出现在队列出参里 */
    private static Report reportWithDetail(
            ReviewTargetType targetType, long targetId, ReportReason reason, String detail) {
        return Report.submit(targetType, targetId, 901L, reason, detail);
    }

    private static Comment comment(long id, String content) {
        Comment comment = Comment.create(1L, 7L, null, content);
        TestFixtures.setId(comment, id);
        return comment;
    }

    private static PostImage image(long postId, String url, int sortOrder) {
        return PostImage.of(postId, url, sortOrder);
    }
}
