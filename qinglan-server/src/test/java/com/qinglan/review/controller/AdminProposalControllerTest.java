package com.qinglan.review.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.EscalatedProposalResponse;
import com.qinglan.review.dto.ProposalSummaryResponse;
import com.qinglan.review.dto.ProposalVoteItem;
import com.qinglan.review.dto.VoteResultResponse;
import com.qinglan.review.entity.ArbitrationDecision;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.VoteDecision;
import com.qinglan.review.service.ProposalService;
import com.qinglan.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 处置提案接口的 Web 层切片（P5 二期，设计 7.4）。
 *
 * <p>只校验参数解析、透传、出参形状与错误码映射；权限点判定（学生 403 / 委员会 200）
 * 由 {@code SecuritySkeletonTest} 在完整上下文中验证（与既有切片同一分工）。</p>
 */
@WebMvcTest(controllers = AdminProposalController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminProposalControllerTest {

    private static final long OPERATOR_ID = 99L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProposalService proposalService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticateAsAdmin() {
        // 认证态按真实过滤器写入的权威集合构造：ROLE_* + 该角色的权限点（P5 权限模型）
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        UserRole.ADMIN.permissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.name())));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(OPERATOR_ID, null, authorities));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("发起提案返回提案号")
    void shouldCreateProposal() throws Exception {
        when(proposalService.create(any(), any())).thenReturn(12L);

        mockMvc.perform(post("/api/admin/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"POST","targetId":7,"action":"TAKE_DOWN","reasonCode":"ABUSE","detail":"人身攻击"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposalId").value(12));
    }

    @Test
    @DisplayName("发起提案缺必填字段返回 400")
    void shouldRejectCreateWithoutAction() throws Exception {
        mockMvc.perform(post("/api/admin/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"POST","targetId":7,"reasonCode":"ABUSE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("同目标已有进行中提案：唯一键冲突映射成 409，不静默吞掉")
    void shouldMapDuplicateOpenProposalTo409() throws Exception {
        when(proposalService.create(any(), any()))
                .thenThrow(new BizException(ErrorCode.PROPOSAL_ALREADY_OPEN));

        mockMvc.perform(post("/api/admin/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"POST","targetId":7,"action":"TAKE_DOWN","reasonCode":"ABUSE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-010"));
    }

    @Test
    @DisplayName("列表：status / mine / cursor 原样透传，出参含汇总不含发起人身份")
    void shouldListProposals() throws Exception {
        when(proposalService.page(eq(ProposalStatus.OPEN), eq(true), eq("1:2"), any())).thenReturn(
                CursorPage.of(List.of(new ProposalSummaryResponse(12L, ReviewTargetType.POST, 7L,
                        "摘要", ProposalAction.TAKE_DOWN, ProposalStatus.OPEN,
                        2, 1, 4, 3, VoteDecision.AGREE, Instant.parse("2026-10-08T00:00:00Z"))), null));

        mockMvc.perform(get("/api/admin/proposals")
                        .param("status", "OPEN")
                        .param("mine", "true")
                        .param("cursor", "1:2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].proposalId").value(12))
                .andExpect(jsonPath("$.data.items[0].agreeCount").value(2))
                .andExpect(jsonPath("$.data.items[0].needed").value(4))
                .andExpect(jsonPath("$.data.items[0].myDecision").value("AGREE"))
                .andExpect(jsonPath("$.data.items[0].proposerId").doesNotExist());
    }

    @Test
    @DisplayName("列表：状态枚举非法返回 400 而不是 500")
    void shouldRejectUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/admin/proposals").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("投票返回判定后的汇总与我的选择")
    void shouldVote() throws Exception {
        when(proposalService.vote(eq(12L), eq(VoteDecision.AGREE), any()))
                .thenReturn(new VoteResultResponse(ProposalStatus.APPROVED, 4, 0, 4, VoteDecision.AGREE));

        mockMvc.perform(post("/api/admin/proposals/{id}/vote", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"AGREE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.needed").value(4));
    }

    @Test
    @DisplayName("重复投票返回 409")
    void shouldMapDuplicateVoteTo409() throws Exception {
        when(proposalService.vote(eq(12L), any(), any()))
                .thenThrow(new BizException(ErrorCode.ALREADY_VOTED));

        mockMvc.perform(post("/api/admin/proposals/{id}/vote", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"AGREE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-011"));
    }

    @Test
    @DisplayName("投票缺 decision 返回 400")
    void shouldRejectVoteWithoutDecision() throws Exception {
        mockMvc.perform(post("/api/admin/proposals/{id}/vote", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("提案不存在返回 404")
    void shouldMapMissingProposalTo404() throws Exception {
        when(proposalService.detail(eq(99L), any()))
                .thenThrow(new BizException(ErrorCode.PROPOSAL_NOT_FOUND));

        mockMvc.perform(get("/api/admin/proposals/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-009"));

        verify(proposalService).detail(eq(99L), any());
    }

    // ------------------------------------------------------------------ 超管裁决

    @Test
    @DisplayName("待裁决列表带每一票明细（仅超管端点，决策 D8）")
    void shouldListEscalatedWithVotes() throws Exception {
        when(proposalService.escalatedPage(eq("1:2"))).thenReturn(CursorPage.of(
                List.of(new EscalatedProposalResponse(12L, ReviewTargetType.POST, 7L, "摘要",
                        ProposalAction.TAKE_DOWN, ProposalStatus.ESCALATED, 1, 3, 3, 4,
                        Instant.parse("2026-10-08T00:00:00Z"),
                        List.of(new ProposalVoteItem(100L, VoteDecision.DISAGREE,
                                Instant.parse("2026-10-08T01:00:00Z"))))),
                null));

        mockMvc.perform(get("/api/admin/proposals/escalated").param("cursor", "1:2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("ESCALATED"))
                .andExpect(jsonPath("$.data.items[0].agreeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].votes[0].voterId").value(100))
                .andExpect(jsonPath("$.data.items[0].votes[0].decision").value("DISAGREE"));
    }

    @Test
    @DisplayName("超管裁决：decision 与 reason 原样透传，操作人取自登录态")
    void shouldArbitrate() throws Exception {
        mockMvc.perform(post("/api/admin/proposals/{id}/arbitrate", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"GRANT","reason":"确认违规"}
                                """))
                .andExpect(status().isOk());

        verify(proposalService).arbitrate(eq(12L), eq(ArbitrationDecision.GRANT), eq("确认违规"), any());
    }

    @Test
    @DisplayName("裁决缺理由返回 400（推翻委员会表决必须留理由）")
    void shouldRejectArbitrateWithoutReason() throws Exception {
        mockMvc.perform(post("/api/admin/proposals/{id}/arbitrate", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"GRANT","reason":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("裁决进行中的提案返回 409（只有僵局才需裁决）")
    void shouldMapArbitrateOnOpenProposalTo409() throws Exception {
        when(proposalService.arbitrate(eq(12L), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION));

        mockMvc.perform(post("/api/admin/proposals/{id}/arbitrate", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"DENY","reason":"证据不足"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QL-409-004"));
    }
}
