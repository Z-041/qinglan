package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.entity.RiskLevel;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.config.ReviewProperties;
import com.qinglan.review.dto.CreateReportRequest;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.support.TestFixtures;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 举报受理：去重（不变量 #4）、聚合计数、达阈值自动下架。
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final long REPORTER_ID = 100L;
    private static final long AUTHOR_ID = 200L;
    private static final long POST_ID = 1L;
    private static final long COMMENT_ID = 7L;
    private static final long TOPIC_ID = 3L;
    private static final int THRESHOLD = 3;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, postRepository, commentRepository,
                userService, auditLogService, new ReviewProperties(THRESHOLD, Duration.ofDays(7)));
    }

    @Test
    @DisplayName("举报帖子：落 PENDING 并让 post.report_count 原子加一")
    void shouldReportPostAndBumpReportCount() {
        Post post = approvedPost(0);
        stubReportablePost(post);
        stubSave();
        stubNoExistingReport(ReviewTargetType.POST, POST_ID);

        reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.ADVERTISEMENT, "  群里天天刷  "));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(captor.getValue().getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(captor.getValue().getDetail()).isEqualTo("群里天天刷");
        verify(postRepository).adjustReportCount(POST_ID, 1);
    }

    @Test
    @DisplayName("不能举报自己发布的内容")
    void shouldRejectSelfReport() {
        Post post = TestFixtures.approvedPost(POST_ID, REPORTER_ID, TOPIC_ID, null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.OTHER, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.SELF_REPORT_FORBIDDEN);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("重复举报返回 409，不静默吞掉（不变量 #4）")
    void shouldRejectDuplicateReport() {
        Post post = approvedPost(0);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(reportRepository.findByTargetTypeAndTargetIdAndReporterId(
                ReviewTargetType.POST, POST_ID, REPORTER_ID))
                .thenReturn(Optional.of(Report.submit(
                        ReviewTargetType.POST, POST_ID, REPORTER_ID, ReportReason.ABUSE, null)));

        assertThatThrownBy(() -> reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.ABUSE, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ALREADY_REPORTED);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("并发下唯一键兜底：插入被拒同样回 409")
    void shouldMapUniqueViolationToConflict() {
        Post post = approvedPost(0);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        stubNoExistingReport(ReviewTargetType.POST, POST_ID);
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("uk_report_target_reporter"));

        assertThatThrownBy(() -> reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.ABUSE, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ALREADY_REPORTED);
        verify(postRepository, never()).adjustReportCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("举报数达阈值：自动下架并留审计，review_by 为空表示非人工处置")
    void shouldAutoTakeDownWhenThresholdReached() {
        Post post = approvedPost(THRESHOLD);
        stubReportablePost(post);
        stubSave();
        stubNoExistingReport(ReviewTargetType.POST, POST_ID);

        reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.FRAUD, null));

        assertThat(post.getStatus()).isEqualTo(PostStatus.TAKEN_DOWN);
        assertThat(post.getReviewBy()).isNull();
        assertThat(post.getReviewReason()).isEqualTo("AUTO_REPORT_THRESHOLD");
        verify(auditLogService).record(AuditTargetType.POST, POST_ID, AuditAction.POST_AUTO_TAKEDOWN,
                AuditLogService.SYSTEM_OPERATOR_ID, "AUTO_REPORT_THRESHOLD");
    }

    @Test
    @DisplayName("举报数未达阈值：不下架、不写审计")
    void shouldNotAutoTakeDownBelowThreshold() {
        Post post = approvedPost(THRESHOLD - 1);
        stubReportablePost(post);
        stubSave();
        stubNoExistingReport(ReviewTargetType.POST, POST_ID);

        reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.FRAUD, null));

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("并发下帖子已被另一请求下架：不重复迁移，避免违反状态机把本请求也回滚掉")
    void shouldSkipAutoTakeDownWhenAlreadyTakenDown() {
        // 第一次读（可见性校验）看到的是 APPROVED 快照，第二次读（阈值复核）看到的是已被下架的版本
        Post approvedSnapshot = approvedPost(THRESHOLD);
        Post takenDownSnapshot = approvedPost(THRESHOLD);
        takenDownSnapshot.transitionTo(new ReviewState.TakenDown(9L, "泄露隐私", Instant.now()));
        // 连续两次 thenReturn 而不是 varargs：后者的泛型数组创建会触发 unchecked 警告（-Werror）
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(approvedSnapshot))
                .thenReturn(Optional.of(takenDownSnapshot));
        stubSave();
        stubNoExistingReport(ReviewTargetType.POST, POST_ID);

        reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.FRAUD, null));

        assertThat(takenDownSnapshot.getReviewBy()).isEqualTo(9L);
        assertThat(takenDownSnapshot.getReviewReason()).isEqualTo("泄露隐私");
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("不可见的帖子按不存在返回，不泄露存在性")
    void shouldRejectReportOnInvisiblePost() {
        Post pending = Post.draft(AUTHOR_ID, TOPIC_ID, "待审正文", null);
        pending.submit(RiskLevel.LOW);
        TestFixtures.setId(pending, POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.POST, POST_ID, ReportReason.ABUSE, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("举报评论：只落举报，不改帖子举报数（评论先发后审，由管理员移除）")
    void shouldReportCommentWithoutBumpingPostCount() {
        Comment comment = Comment.create(POST_ID, AUTHOR_ID, null, "一句冒犯的话");
        TestFixtures.setId(comment, COMMENT_ID);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(TestFixtures.approvedPost(POST_ID, AUTHOR_ID, TOPIC_ID, null)));
        stubSave();
        stubNoExistingReport(ReviewTargetType.COMMENT, COMMENT_ID);

        reportService.report(REPORTER_ID, new CreateReportRequest(
                ReviewTargetType.COMMENT, COMMENT_ID, ReportReason.ABUSE, null));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo(ReviewTargetType.COMMENT);
        verify(postRepository, never()).adjustReportCount(anyLong(), anyInt());
    }

    private Post approvedPost(int reportCount) {
        Post post = TestFixtures.approvedPost(POST_ID, AUTHOR_ID, TOPIC_ID, null);
        TestFixtures.setReportCount(post, reportCount);
        return post;
    }

    private void stubReportablePost(Post post) {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
    }

    private void stubNoExistingReport(ReviewTargetType targetType, Long targetId) {
        when(reportRepository.findByTargetTypeAndTargetIdAndReporterId(targetType, targetId, REPORTER_ID))
                .thenReturn(Optional.empty());
    }

    private void stubSave() {
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            TestFixtures.setId(report, 999L);
            return report;
        });
    }
}
