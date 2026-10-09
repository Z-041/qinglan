package com.qinglan.review.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAppealItemResponse;
import com.qinglan.review.dto.AdminReviewContextResponse;
import com.qinglan.review.dto.AdminReviewHistoryResponse;
import com.qinglan.review.dto.AdminTodoCountsResponse;
import com.qinglan.review.dto.CreateProposalRequest;
import com.qinglan.review.dto.PendingPostItemResponse;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.ReviewQueueProposalResponse;
import com.qinglan.review.dto.ReviewQueueQuery;
import com.qinglan.review.dto.ReviewQueueSort;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.VoteDecision;
import com.qinglan.review.service.AdminTodoCountService;
import com.qinglan.review.service.AppealQueueService;
import com.qinglan.review.service.AppealService;
import com.qinglan.review.service.PendingPostQueueService;
import com.qinglan.review.service.ProposalService;
import com.qinglan.review.service.ReviewContextService;
import com.qinglan.review.service.ReviewQueueService;
import com.qinglan.review.service.ReviewService;
import com.qinglan.user.dto.ReasonRequest;
import com.qinglan.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审核工作台接口的参数校验与操作人取值（Web 层切片）。
 *
 * <p>权限判定（学生 403、管理员放行）由 {@code SecuritySkeletonTest} 在完整上下文中验证，
 * 此处关闭过滤器只校验 DTO 约束、状态码与「操作人取自登录态」。</p>
 */
@WebMvcTest(controllers = AdminReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminReviewControllerTest {

    private static final long OPERATOR_ID = 99L;
    private static final long POST_ID = 1L;
    /** 无筛选时的队列条件（与控制器默认构造的一致） */
    private static final ReviewQueueQuery DEFAULT_QUERY = new ReviewQueueQuery(null, null, false, null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private ReviewQueueService reviewQueueService;

    @MockitoBean
    private ReviewContextService reviewContextService;

    @MockitoBean
    private AppealService appealService;

    @MockitoBean
    private AppealQueueService appealQueueService;

    @MockitoBean
    private PendingPostQueueService pendingPostQueueService;

    @MockitoBean
    private ProposalService proposalService;

    @MockitoBean
    private AdminTodoCountService adminTodoCountService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 认证态按真实过滤器写入的权威集合构造：ROLE_* + 该角色的权限点（P5 权限模型） */
    private void authenticateAsAdmin() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        UserRole.ADMIN.permissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.name())));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(OPERATOR_ID, null, authorities));
    }

    // ------------------------------------------------------------------ 队列

    @Test
    @DisplayName("队列返回游标分页，且 nextCursor 是字符串（前端原样回传）")
    void shouldReturnQueueWithStringCursor() throws Exception {
        authenticateAsAdmin();
        when(reviewQueueService.queue("2:1000:1", DEFAULT_QUERY, OPERATOR_ID)).thenReturn(CursorPage.of(
                List.of(new ReviewQueueItemResponse(ReviewTargetType.POST, POST_ID, "摘要",
                        List.of("/uploads/a.jpg", "/uploads/b.jpg"), "APPROVED",
                        2, Instant.parse("2026-10-06T00:00:00Z"), List.of(ReportReason.OTHER),
                        List.of("疑似中介在拉群"), null)),
                "3:2000:1"));

        mockMvc.perform(get("/api/admin/review-queue").param("cursor", "2:1000:1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(POST_ID))
                .andExpect(jsonPath("$.data.items[0].reportCount").value(2))
                // 全部图片按序出参：管理员放行前必须看得见图
                .andExpect(jsonPath("$.data.items[0].images[0]").value("/uploads/a.jpg"))
                .andExpect(jsonPath("$.data.items[0].images[1]").value("/uploads/b.jpg"))
                // 举报人填的补充说明必须能读到：理由选「其他」时它是唯一线索
                .andExpect(jsonPath("$.data.items[0].details[0]").value("疑似中介在拉群"))
                // 没有进行中提案时该字段为 null，前端无需判空
                .andExpect(jsonPath("$.data.items[0].proposal").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("3:2000:1"));
    }

    @Test
    @DisplayName("队列行带进行中提案的进度，且只回我自己那一票（决策 D8）")
    void shouldReturnQueueItemWithProposalProgress() throws Exception {
        authenticateAsAdmin();
        when(reviewQueueService.queue(null, DEFAULT_QUERY, OPERATOR_ID)).thenReturn(CursorPage.of(
                List.of(new ReviewQueueItemResponse(ReviewTargetType.POST, POST_ID, "摘要", List.of(),
                        "APPROVED",
                        1, Instant.parse("2026-10-06T00:00:00Z"), List.of(ReportReason.ABUSE),
                        List.of(),
                        new ReviewQueueProposalResponse(12L, ProposalAction.TAKE_DOWN, ProposalStatus.OPEN,
                                2, 4, 3, VoteDecision.AGREE))),
                null));

        mockMvc.perform(get("/api/admin/review-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].proposal.proposalId").value(12))
                .andExpect(jsonPath("$.data.items[0].proposal.agreeCount").value(2))
                .andExpect(jsonPath("$.data.items[0].proposal.needed").value(4))
                .andExpect(jsonPath("$.data.items[0].proposal.myDecision").value("AGREE"))
                // 不暴露任何投票人身份
                .andExpect(jsonPath("$.data.items[0].proposal.votes").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].proposal.voterId").doesNotExist());
    }

    @Test
    @DisplayName("不带游标即取首页，游标参数对服务层为空")
    void shouldRequestFirstPageWhenCursorAbsent() throws Exception {
        authenticateAsAdmin();
        when(reviewQueueService.queue(null, DEFAULT_QUERY, OPERATOR_ID)).thenReturn(CursorPage.empty());

        mockMvc.perform(get("/api/admin/review-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(reviewQueueService).queue(null, DEFAULT_QUERY, OPERATOR_ID);
    }

    @Test
    @DisplayName("队列筛选与排序参数被解析进查询条件（理由多选 / 目标类型 / 只看超时 / 排序）")
    void shouldParseQueueFilterParams() throws Exception {
        authenticateAsAdmin();
        ReviewQueueQuery expected = new ReviewQueueQuery(
                List.of(ReportReason.ABUSE, ReportReason.PRIVACY), ReviewTargetType.POST, true,
                ReviewQueueSort.FIRST_REPORTED_ASC);
        when(reviewQueueService.queue(null, expected, OPERATOR_ID)).thenReturn(CursorPage.empty());

        mockMvc.perform(get("/api/admin/review-queue")
                        .param("reasons", "ABUSE", "PRIVACY")
                        .param("targetType", "POST")
                        .param("overdueOnly", "true")
                        .param("sort", "FIRST_REPORTED_ASC"))
                .andExpect(status().isOk());

        verify(reviewQueueService).queue(null, expected, OPERATOR_ID);
    }

    @Test
    @DisplayName("非法排序口径返回 400 而不是 500")
    void shouldRejectUnknownSort() throws Exception {
        mockMvc.perform(get("/api/admin/review-queue").param("sort", "NOT_A_SORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    // ------------------------------------------------------------------ 待办计数 / 历史

    @Test
    @DisplayName("待办计数：无权限的分组不返回（不是返回 0）")
    void shouldOmitTodoGroupWithoutPermission() throws Exception {
        authenticateAsAdmin();
        when(adminTodoCountService.counts(OPERATOR_ID))
                .thenReturn(new AdminTodoCountsResponse(3L, 2L, 1L, null));

        mockMvc.perform(get("/api/admin/todo-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPosts").value(3))
                .andExpect(jsonPath("$.data.pendingReports").value(2))
                .andExpect(jsonPath("$.data.proposable").value(1))
                // 无 PROPOSAL_ARBITRATE 时该组整个缺席，而不是 0
                .andExpect(jsonPath("$.data.escalated").doesNotExist());
    }

    @Test
    @DisplayName("上下文历史：返回作者最近动态与处置流水，且不含作者/举报人身份")
    void shouldReturnReviewHistory() throws Exception {
        when(reviewContextService.history(ReviewTargetType.POST, 5L)).thenReturn(
                new AdminReviewHistoryResponse(
                        List.of(new AdminReviewHistoryResponse.AuthorPostItem(
                                9L, "APPROVED", Instant.parse("2026-10-01T00:00:00Z"))),
                        List.of(new AdminReviewHistoryResponse.DispositionItem(
                                AuditAction.POST_TAKEDOWN, 99L, "ABUSE", Instant.parse("2026-10-02T00:00:00Z")))));

        mockMvc.perform(get("/api/admin/review-context/history")
                        .param("targetType", "POST")
                        .param("targetId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorRecentPosts[0].id").value(9))
                .andExpect(jsonPath("$.data.authorRecentPosts[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.targetDispositions[0].action").value("POST_TAKEDOWN"))
                .andExpect(jsonPath("$.data.targetDispositions[0].operatorId").value(99))
                .andExpect(jsonPath("$.data.authorRecentPosts[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.data.authorRecentPosts[0].anonymousCode").doesNotExist())
                .andExpect(jsonPath("$.data.targetDispositions[0].studentId").doesNotExist());
    }

    @Test
    @DisplayName("上下文历史缺少 targetId 返回 400")
    void shouldRejectHistoryWithoutTargetId() throws Exception {
        mockMvc.perform(get("/api/admin/review-context/history").param("targetType", "POST"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 待审帖子

    @Test
    @DisplayName("待审队列返回游标分页，且行内不含作者身份字段")
    void shouldReturnPendingPostsWithoutAuthorIdentity() throws Exception {
        when(pendingPostQueueService.queue(null)).thenReturn(CursorPage.of(
                List.of(new PendingPostItemResponse(POST_ID, "待审正文", List.of("/uploads/p.jpg"), "PENDING",
                        Instant.parse("2026-10-06T00:00:00Z"), 0)),
                "1000:1"));

        mockMvc.perform(get("/api/admin/pending-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].postId").value(POST_ID))
                // 全部图片按序出参：管理员放行前必须看得见图
                .andExpect(jsonPath("$.data.items[0].images[0]").value("/uploads/p.jpg"))
                .andExpect(jsonPath("$.data.items[0].targetStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].anonymousCode").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("1000:1"));
    }

    @Test
    @DisplayName("待审队列的游标原样透传给服务层")
    void shouldPassPendingPostsCursor() throws Exception {
        when(pendingPostQueueService.queue("1000:1")).thenReturn(CursorPage.empty());

        mockMvc.perform(get("/api/admin/pending-posts").param("cursor", "1000:1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(pendingPostQueueService).queue("1000:1");
    }

    // ------------------------------------------------------------------ 上下文

    @Test
    @DisplayName("上下文接口返回信用分与举报聚合，且不含作者身份字段")
    void shouldReturnReviewContext() throws Exception {
        when(reviewContextService.context(ReviewTargetType.POST, 5L)).thenReturn(
                new AdminReviewContextResponse(ReviewTargetType.POST, 5L, "APPROVED", 96, 3L, 2, 3, 2L,
                        List.of(ReportReason.ABUSE)));

        mockMvc.perform(get("/api/admin/review-context")
                        .param("targetType", "POST")
                        .param("targetId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorCreditScore").value(96))
                .andExpect(jsonPath("$.data.repeatedReporterCount").value(2))
                .andExpect(jsonPath("$.data.authorId").doesNotExist())
                .andExpect(jsonPath("$.data.reporterIds").doesNotExist());
    }

    @Test
    @DisplayName("缺少 targetId 返回 400")
    void shouldRejectContextWithoutTargetId() throws Exception {
        mockMvc.perform(get("/api/admin/review-context").param("targetType", "POST"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 处置

    @Test
    @DisplayName("下架改为发起提案：动作由 URL 隐含，操作人取自登录态")
    void shouldProposeTakeDownWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();
        when(proposalService.create(any(), any())).thenReturn(12L);

        mockMvc.perform(post("/api/admin/posts/{postId}/take-down", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"ABUSE\",\"detail\":\"人身攻击\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposalId").value(12));

        ArgumentCaptor<CreateProposalRequest> captor = ArgumentCaptor.forClass(CreateProposalRequest.class);
        verify(proposalService).create(captor.capture(), eq(OPERATOR_ID));
        assertThat(captor.getValue().action()).isEqualTo(ProposalAction.TAKE_DOWN);
        assertThat(captor.getValue().targetType()).isEqualTo(ReviewTargetType.POST);
        assertThat(captor.getValue().targetId()).isEqualTo(POST_ID);
        assertThat(captor.getValue().reasonCode()).isEqualTo(ReportReason.ABUSE);
        assertThat(captor.getValue().detail()).isEqualTo("人身攻击");
    }

    @Test
    @DisplayName("移除评论改为发起提案：目标类型由动作推导为 COMMENT")
    void shouldProposeCommentRemoval() throws Exception {
        authenticateAsAdmin();
        when(proposalService.create(any(), any())).thenReturn(13L);

        mockMvc.perform(post("/api/admin/comments/{commentId}/remove", 55L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"ABUSE\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateProposalRequest> captor = ArgumentCaptor.forClass(CreateProposalRequest.class);
        verify(proposalService).create(captor.capture(), eq(OPERATOR_ID));
        assertThat(captor.getValue().action()).isEqualTo(ProposalAction.REMOVE_COMMENT);
        assertThat(captor.getValue().targetType()).isEqualTo(ReviewTargetType.COMMENT);
    }

    @Test
    @DisplayName("隐藏联系方式与维持下架同样只是发起提案")
    void shouldProposeHideContactAndUphold() throws Exception {
        authenticateAsAdmin();
        when(proposalService.create(any(), any())).thenReturn(14L);

        mockMvc.perform(post("/api/admin/posts/{postId}/hide-contact", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"FRAUD\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/posts/{postId}/uphold-reports", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"ABUSE\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateProposalRequest> captor = ArgumentCaptor.forClass(CreateProposalRequest.class);
        verify(proposalService, times(2)).create(captor.capture(), eq(OPERATOR_ID));
        assertThat(captor.getAllValues()).extracting(CreateProposalRequest::action)
                .containsExactly(ProposalAction.HIDE_CONTACT, ProposalAction.UPHOLD_TAKEDOWN);
    }

    @Test
    @DisplayName("下架缺少违规类型返回 400")
    void shouldRejectTakeDownWithoutReasonCode() throws Exception {
        mockMvc.perform(post("/api/admin/posts/{postId}/take-down", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detail\":\"说明\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("下架说明超长返回 400")
    void shouldRejectTakeDownWithExcessiveDetail() throws Exception {
        mockMvc.perform(post("/api/admin/posts/{postId}/take-down", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"ABUSE\",\"detail\":\"" + "长".repeat(401) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("恢复可见仍是单人动作，直接调用审核服务，不产生提案")
    void shouldRestoreDirectly() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/posts/{postId}/restore", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"确系误判\"}"))
                .andExpect(status().isOk());

        verify(reviewService).restore(POST_ID, OPERATOR_ID, "确系误判");
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("驳回理由为空返回 400")
    void shouldRejectBlankRejectReason() throws Exception {
        mockMvc.perform(post("/api/admin/posts/{postId}/reject", POST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("放行不带请求体，操作人取自登录态")
    void shouldApproveWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/posts/{postId}/approve", POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(reviewService).approve(POST_ID, OPERATOR_ID);
    }

    @Test
    @DisplayName("驳回举报缺少目标 id 返回 400")
    void shouldRejectDismissWithoutTargetId() throws Exception {
        mockMvc.perform(post("/api/admin/reports/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"reason\":\"内容合规\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    // ------------------------------------------------------------------ 申诉复审

    @Test
    @DisplayName("复审通过调用服务，操作人取自登录态")
    void shouldAcceptAppealWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/appeals/{appealId}/accept", 500L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"确系误判\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(appealService).accept(500L, OPERATOR_ID, "确系误判");
    }

    @Test
    @DisplayName("复审不通过调用服务，操作人取自登录态")
    void shouldRejectAppealWithOperatorFromPrincipal() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(post("/api/admin/appeals/{appealId}/reject", 500L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"维持原判\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(appealService).reject(500L, OPERATOR_ID, "维持原判");
    }

    @Test
    @DisplayName("复审理由为空返回 400")
    void shouldRejectBlankAppealReason() throws Exception {
        mockMvc.perform(post("/api/admin/appeals/{appealId}/accept", 500L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("申诉队列返回游标分页，且响应不含申诉人字段")
    void shouldReturnAppealQueueWithoutAppellant() throws Exception {
        when(appealQueueService.queue(null)).thenReturn(CursorPage.of(
                List.of(new AdminAppealItemResponse(500L, ReviewTargetType.POST, POST_ID, "摘要",
                        "TAKEN_DOWN", "ABUSE: 人身攻击", "误判", Instant.parse("2026-10-06T00:00:00Z"))),
                "600"));

        mockMvc.perform(get("/api/admin/appeals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].appealId").value(500L))
                .andExpect(jsonPath("$.data.items[0].targetId").value(POST_ID))
                .andExpect(jsonPath("$.data.items[0].takenDownReason").value("ABUSE: 人身攻击"))
                .andExpect(jsonPath("$.data.items[0].appellantId").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("600"));
    }
}
