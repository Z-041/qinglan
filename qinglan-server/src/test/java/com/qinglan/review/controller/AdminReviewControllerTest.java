package com.qinglan.review.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAppealItemResponse;
import com.qinglan.review.dto.AdminReviewContextResponse;
import com.qinglan.review.dto.CreateProposalRequest;
import com.qinglan.review.dto.PendingPostItemResponse;
import com.qinglan.review.dto.ReviewQueueItemResponse;
import com.qinglan.review.dto.ReviewQueueProposalResponse;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.VoteDecision;
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
        when(reviewQueueService.queue("2:1000:1", OPERATOR_ID)).thenReturn(CursorPage.of(
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
        when(reviewQueueService.queue(null, OPERATOR_ID)).thenReturn(CursorPage.of(
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
        when(reviewQueueService.queue(null, OPERATOR_ID)).thenReturn(CursorPage.empty());

        mockMvc.perform(get("/api/admin/review-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(reviewQueueService).queue(null, OPERATOR_ID);
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
