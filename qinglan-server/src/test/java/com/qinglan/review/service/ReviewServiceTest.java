package com.qinglan.review.service;

import com.qinglan.comment.service.CommentService;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.post.entity.ContactVisibility;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.entity.RiskLevel;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.DismissReportsRequest;
import com.qinglan.review.dto.TakeDownPostRequest;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.support.TestFixtures;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 审核处置的单元测试。
 *
 * <p>盯三件事：状态迁移必须过状态机（不变量 #5）、每个处置都留痕（不变量 #7）、
 * 处置完的举报必须结案且计数不残留（否则队列里会一直挂着一行已处理的内容）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final long POST_ID = 1L;
    private static final long AUTHOR_ID = 7L;
    private static final long OPERATOR_ID = 99L;
    private static final long COMMENT_ID = 55L;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private CommentService commentService;

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private ReviewService reviewService;

    // -------------------------------------------------------- 放行 / 驳回（自 post 模块迁入）

    @Test
    @DisplayName("不变量 #5：放行需经 Reviewing 走到 Approved，并写审计日志")
    void shouldApproveThroughReviewing() {
        Post pending = pendingPost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pending));

        reviewService.approve(POST_ID, OPERATOR_ID);

        assertThat(pending.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(pending.getReviewBy()).isEqualTo(OPERATOR_ID);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.POST_APPROVE, OPERATOR_ID, null);
    }

    @Test
    @DisplayName("不变量 #5：已通过的帖子再次放行属非法迁移，且不留审计")
    void shouldRejectApproveWhenAlreadyApproved() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));

        assertThatThrownBy(() -> reviewService.approve(POST_ID, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("驳回写入理由，作者在详情里能看到该理由")
    void shouldRejectWithReasonVisibleToAuthor() {
        Post pending = pendingPost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pending));

        reviewService.reject(POST_ID, OPERATOR_ID, "正文含他人手机号");

        assertThat(pending.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(pending.getReviewReason()).isEqualTo("正文含他人手机号");
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.POST_REJECT, OPERATOR_ID, "正文含他人手机号");
    }

    // -------------------------------------------------------- 下架

    @Test
    @DisplayName("下架：状态走 Approved→TakenDown，结案全部待处理举报，并把待处理计数减回 0")
    void shouldTakeDownAndSettleReports() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of(postReport(101L), postReport(102L)));

        reviewService.takeDown(POST_ID, OPERATOR_ID,
                new TakeDownPostRequest(ReportReason.ABUSE, "人身攻击", null));

        Post post = postRepository.findById(POST_ID).orElseThrow();
        assertThat(post.getStatus()).isEqualTo(PostStatus.TAKEN_DOWN);
        assertThat(post.getReviewBy()).isEqualTo(OPERATOR_ID);
        assertThat(post.getReviewReason()).isEqualTo("ABUSE: 人身攻击");
        verify(reportRepository, never()).save(any());
        // 举报随内容被处置而成立，计数按结案条数原子回退
        verify(postRepository).adjustReportCount(POST_ID, -2);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.POST_TAKEDOWN, OPERATOR_ID, "ABUSE: 人身攻击");
    }

    @Test
    @DisplayName("下架不带说明时理由退化为违规类型码，便于按码统计")
    void shouldTakeDownWithReasonCodeOnly() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of());

        reviewService.takeDown(POST_ID, OPERATOR_ID,
                new TakeDownPostRequest(ReportReason.PORNOGRAPHY, "   ", null));

        assertThat(postRepository.findById(POST_ID).orElseThrow().getReviewReason())
                .isEqualTo("PORNOGRAPHY");
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.POST_TAKEDOWN, OPERATOR_ID, "PORNOGRAPHY");
    }

    @Test
    @DisplayName("下架并封禁：带 banDays 时同时封禁作者，封禁自身也留痕")
    void shouldBanAuthorWhenTakeDownWithBanDays() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of());

        reviewService.takeDown(POST_ID, OPERATOR_ID,
                new TakeDownPostRequest(ReportReason.ABUSE, "累犯", 7));

        verify(userService).ban(AUTHOR_ID, Duration.ofDays(7), "ABUSE: 累犯", OPERATOR_ID);
    }

    @Test
    @DisplayName("不在审核通过态的内容不能下架（不变量 #5 禁止跳态），且不结案举报")
    void shouldRejectTakeDownFromIllegalState() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pendingPost()));

        assertThatThrownBy(() -> reviewService.takeDown(POST_ID, OPERATOR_ID,
                new TakeDownPostRequest(ReportReason.ABUSE, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(reportRepository, auditLogService, userService);
    }

    // -------------------------------------------------------- 恢复 / 隐藏联系方式

    @Test
    @DisplayName("恢复可见：状态回到 Approved，并写审计")
    void shouldRestoreTakenDownPost() {
        Post takenDown = takenDownPost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDown));

        reviewService.restore(POST_ID, OPERATOR_ID, "申诉理由成立");

        assertThat(takenDown.getStatus()).isEqualTo(PostStatus.APPROVED);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.POST_RESTORE, OPERATOR_ID, "申诉理由成立");
    }

    @Test
    @DisplayName("恢复可见：已是可见态时幂等跳过，不迁移也不留审计（复审撞上他人已恢复不应整体回滚）")
    void shouldSkipRestoreWhenAlreadyApproved() {
        Post post = approvedPost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        reviewService.restore(POST_ID, OPERATOR_ID, "误操作");

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("未审核通过也不在下架态的帖子不能「恢复」（不变量 #5 禁止跳态）")
    void shouldRejectRestoreFromPending() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pendingPost()));

        assertThatThrownBy(() -> reviewService.restore(POST_ID, OPERATOR_ID, "误操作"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("隐藏联系方式：只改可见性、不清空值（处罚可能被撤销），并写审计")
    void shouldHideContactWithoutWipingValue() {
        Post post = approvedPost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        reviewService.hideContact(POST_ID, OPERATOR_ID, "疑似诈骗");

        assertThat(post.getContactVisibility()).isEqualTo(ContactVisibility.HIDDEN);
        assertThat(post.getContact()).isEqualTo("wx-demo");
        // 隐藏后只有作者本人可见，认证过的他人也拿不到
        assertThat(post.contactVisibleTo(8L, true)).isFalse();
        assertThat(post.contactVisibleTo(AUTHOR_ID, true)).isTrue();
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.POST_HIDE_CONTACT, OPERATOR_ID, "疑似诈骗");
    }

    @Test
    @DisplayName("撤销隐藏联系方式：可见性改回 VISIBLE，联系方式对认证用户恢复可见，并写审计")
    void shouldShowContactBackAfterHide() {
        Post post = approvedPost();
        post.hideContact();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        reviewService.showContact(POST_ID, OPERATOR_ID, "误判，恢复");

        assertThat(post.getContactVisibility()).isEqualTo(ContactVisibility.VISIBLE);
        assertThat(post.contactVisibleTo(8L, true)).isTrue();
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.POST_SHOW_CONTACT, OPERATOR_ID, "误判，恢复");
    }

    // -------------------------------------------------------- 维持下架（自动降级的人工确认）

    @Test
    @DisplayName("维持下架：已下架帖的待处理举报全部结案，计数按结案条数原子回退，并写审计")
    void shouldUpholdReportsForTakenDownPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost()));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of(postReport(101L), postReport(102L)));

        reviewService.upholdReports(POST_ID, OPERATOR_ID, "确认违规，维持下架");

        // 内容状态不变：维持下架是结案举报，不是又一次下架
        assertThat(postRepository.findById(POST_ID).orElseThrow().getStatus())
                .isEqualTo(PostStatus.TAKEN_DOWN);
        verify(postRepository).adjustReportCount(POST_ID, -2);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.REPORT_UPHOLD, OPERATOR_ID, "确认违规，维持下架");
    }

    @Test
    @DisplayName("内容仍可见时不能「维持下架」，且不碰举报与审计")
    void shouldRejectUpholdWhenPostStillVisible() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));

        assertThatThrownBy(() -> reviewService.upholdReports(POST_ID, OPERATOR_ID, "维持下架"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(reportRepository, auditLogService);
    }

    @Test
    @DisplayName("没有待处理举报时「维持下架」按「没有待处理的举报」返回，避免空操作留下假审计")
    void shouldRejectUpholdWhenNothingPending() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost()));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of());

        assertThatThrownBy(() -> reviewService.upholdReports(POST_ID, OPERATOR_ID, "维持下架"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
        verify(postRepository, never()).adjustReportCount(any(), anyInt());
    }

    // -------------------------------------------------------- 移除评论

    @Test
    @DisplayName("移除评论：委托评论模块做清理，并结案该评论上的待处理举报")
    void shouldRemoveCommentAndSettleReports() {
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.COMMENT, COMMENT_ID, ReportStatus.PENDING))
                .thenReturn(List.of(commentReport(201L)));

        reviewService.removeComment(COMMENT_ID, OPERATOR_ID, "人身攻击");

        verify(commentService).removeByAdmin(COMMENT_ID, OPERATOR_ID, "人身攻击");
        // 评论没有 report_count 列，不应误动帖子的计数
        verifyNoInteractions(postRepository);
    }

    // -------------------------------------------------------- 驳回举报

    @Test
    @DisplayName("驳回举报：该目标下所有待处理举报一次结案，帖子待处理计数同步清零")
    void shouldDismissAllPendingReports() {
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of(postReport(301L), postReport(302L), postReport(303L)));

        reviewService.dismissReports(
                new DismissReportsRequest(ReviewTargetType.POST, POST_ID, "内容合规"), OPERATOR_ID);

        verify(postRepository).adjustReportCount(POST_ID, -3);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.REPORT_DISMISS, OPERATOR_ID, "内容合规");
    }

    @Test
    @DisplayName("驳回评论举报不涉及帖子计数")
    void shouldNotTouchPostCountWhenDismissingCommentReports() {
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.COMMENT, COMMENT_ID, ReportStatus.PENDING))
                .thenReturn(List.of(commentReport(401L)));

        reviewService.dismissReports(
                new DismissReportsRequest(ReviewTargetType.COMMENT, COMMENT_ID, null), OPERATOR_ID);

        verifyNoInteractions(postRepository);
        verify(auditLogService).record(AuditTargetType.COMMENT, COMMENT_ID,
                AuditAction.REPORT_DISMISS, OPERATOR_ID, null);
    }

    @Test
    @DisplayName("没有待处理举报时驳回按「没有待处理的举报」返回，避免空操作留下假审计")
    void shouldRejectDismissWhenNothingPending() {
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of());

        assertThatThrownBy(() -> reviewService.dismissReports(
                new DismissReportsRequest(ReviewTargetType.POST, POST_ID, "内容合规"), OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
        verifyNoInteractions(auditLogService, postRepository);
    }

    @Test
    @DisplayName("帖子不存在时下架按「帖子不存在」处理")
    void shouldRejectTakeDownOfMissingPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.takeDown(POST_ID, OPERATOR_ID,
                new TakeDownPostRequest(ReportReason.OTHER, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(reportRepository, auditLogService, userService);
    }

    // -------------------------------------------------------- 通知（REVIEW_RESULT / REPORT_FEEDBACK）

    @Test
    @DisplayName("放行通知作者：REVIEW_RESULT「已通过审核」")
    void shouldNotifyAuthorOnApprove() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pendingPost()));

        reviewService.approve(POST_ID, OPERATOR_ID);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPort).notify(eq(AUTHOR_ID), eq(OPERATOR_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.REVIEW_RESULT);
        assertThat(captor.getValue().title()).isEqualTo("你的帖子已通过审核");
        assertThat(captor.getValue().link()).isEqualTo("/posts/" + POST_ID);
    }

    @Test
    @DisplayName("驳回通知作者：REVIEW_RESULT「未通过审核」，body 为驳回理由")
    void shouldNotifyAuthorOnReject() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(pendingPost()));

        reviewService.reject(POST_ID, OPERATOR_ID, "正文含他人手机号");

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPort).notify(eq(AUTHOR_ID), eq(OPERATOR_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.REVIEW_RESULT);
        assertThat(captor.getValue().title()).isEqualTo("你的帖子未通过审核");
        assertThat(captor.getValue().body()).isEqualTo("正文含他人手机号");
    }

    @Test
    @DisplayName("下架通知作者，并分别通知该帖各举报人「已处理」")
    void shouldNotifyAuthorAndReportersOnTakeDown() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of(postReport(101L), postReport(102L)));

        reviewService.takeDown(POST_ID, OPERATOR_ID,
                new TakeDownPostRequest(ReportReason.ABUSE, "人身攻击", null));

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPort).notify(eq(AUTHOR_ID), eq(OPERATOR_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.REVIEW_RESULT);
        assertThat(captor.getValue().title()).isEqualTo("你的帖子已被下架");
        assertThat(captor.getValue().body()).isEqualTo("ABUSE: 人身攻击");

        verify(notificationPort).notify(eq(101L), eq(OPERATOR_ID), any(NotificationMessage.class));
        verify(notificationPort).notify(eq(102L), eq(OPERATOR_ID), any(NotificationMessage.class));
    }

    @Test
    @DisplayName("驳回举报：分别通知各举报人「未获支持」")
    void shouldNotifyReportersOnDismiss() {
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of(postReport(301L), postReport(302L)));

        reviewService.dismissReports(
                new DismissReportsRequest(ReviewTargetType.POST, POST_ID, "内容合规"), OPERATOR_ID);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPort).notify(eq(301L), eq(OPERATOR_ID), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.REPORT_FEEDBACK);
        assertThat(captor.getValue().title()).isEqualTo("你的举报未获支持");
        assertThat(captor.getValue().link()).isEqualTo("/posts/" + POST_ID);
        verify(notificationPort).notify(eq(302L), eq(OPERATOR_ID), any(NotificationMessage.class));
    }

    // ------------------------------------------------------------------ 辅助

    private static Post approvedPost() {
        return TestFixtures.approvedPost(POST_ID, AUTHOR_ID, 3L, "wx-demo");
    }

    private static Post pendingPost() {
        Post pending = Post.draft(AUTHOR_ID, 3L, "待审正文", null);
        pending.submit(RiskLevel.LOW);
        TestFixtures.setId(pending, POST_ID);
        return pending;
    }

    private static Post takenDownPost() {
        Post post = approvedPost();
        post.transitionTo(new ReviewState.TakenDown(OPERATOR_ID, "ABUSE", Instant.now()));
        return post;
    }

    private static Report postReport(long reporterId) {
        return Report.submit(ReviewTargetType.POST, POST_ID, reporterId, ReportReason.ABUSE, null);
    }

    private static Report commentReport(long reporterId) {
        return Report.submit(ReviewTargetType.COMMENT, COMMENT_ID, reporterId, ReportReason.ABUSE, null);
    }
}
