package com.qinglan.comment.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.comment.dto.CommentResponse;
import com.qinglan.comment.service.CommentService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.interaction.LikeStateResponse;
import com.qinglan.common.web.CursorPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论接口的参数校验与状态码映射（Web 层切片）。
 *
 * <p>列表对游客开放，写操作需要登录；此处置信 Service 的可见性判定，只校验 401、DTO 约束的 400、
 * 以及服务层透传的 403/404。</p>
 */
@WebMvcTest(controllers = CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

    private static final long USER_ID = 7L;
    private static final long POST_ID = 1L;
    private static final long COMMENT_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(USER_ID, null, List.of()));
    }

    private static CommentResponse commentResponse() {
        return new CommentResponse(COMMENT_ID, "同问", "代号A", 0L, false, true,
                Instant.parse("2026-10-07T00:00:00Z"), List.of());
    }

    @Test
    @DisplayName("评论列表返回 200，游客可读")
    void shouldListComments() throws Exception {
        when(commentService.list(eq(POST_ID), isNull(), any())).thenReturn(CursorPage.of(
                List.of(commentResponse()), "5"));

        mockMvc.perform(get("/api/posts/" + POST_ID + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].id").value(COMMENT_ID))
                .andExpect(jsonPath("$.data.nextCursor").value("5"));
    }

    @Test
    @DisplayName("发表评论成功，评论者取自登录态")
    void shouldCreateComment() throws Exception {
        authenticate();
        when(commentService.create(eq(POST_ID), eq(USER_ID), any())).thenReturn(commentResponse());

        mockMvc.perform(post("/api/posts/" + POST_ID + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"同问\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.id").value(COMMENT_ID));

        verify(commentService).create(eq(POST_ID), eq(USER_ID), any());
    }

    @Test
    @DisplayName("评论正文为空返回 400，并给出校验消息")
    void shouldRejectBlankComment() throws Exception {
        mockMvc.perform(post("/api/posts/" + POST_ID + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"))
                .andExpect(jsonPath("$.message").value("评论不能为空"));
    }

    @Test
    @DisplayName("未登录发表评论返回 401")
    void shouldRejectAnonymousCreate() throws Exception {
        mockMvc.perform(post("/api/posts/" + POST_ID + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"同问\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("帖子不可见时发表评论返回 404")
    void shouldReturn404OnInvisiblePost() throws Exception {
        authenticate();
        doThrow(new BizException(ErrorCode.POST_NOT_FOUND))
                .when(commentService).create(eq(POST_ID), eq(USER_ID), any());

        mockMvc.perform(post("/api/posts/" + POST_ID + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"同问\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-002"));
    }

    @Test
    @DisplayName("删除自己的评论返回 200")
    void shouldDeleteComment() throws Exception {
        authenticate();

        mockMvc.perform(delete("/api/comments/" + COMMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"));

        verify(commentService).delete(COMMENT_ID, USER_ID);
    }

    @Test
    @DisplayName("未登录删除评论返回 401")
    void shouldRejectAnonymousDelete() throws Exception {
        mockMvc.perform(delete("/api/comments/" + COMMENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("删除他人评论返回 403")
    void shouldRejectDeletingOthersComment() throws Exception {
        authenticate();
        doThrow(new BizException(ErrorCode.NOT_CONTENT_OWNER))
                .when(commentService).delete(COMMENT_ID, USER_ID);

        mockMvc.perform(delete("/api/comments/" + COMMENT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-003"));
    }

    @Test
    @DisplayName("评论不存在返回 404")
    void shouldReturn404WhenCommentMissing() throws Exception {
        authenticate();
        doThrow(new BizException(ErrorCode.COMMENT_NOT_FOUND))
                .when(commentService).delete(COMMENT_ID, USER_ID);

        mockMvc.perform(delete("/api/comments/" + COMMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-004"));
    }

    @Test
    @DisplayName("点赞评论返回最新点赞状态")
    void shouldLikeComment() throws Exception {
        authenticate();
        when(commentService.like(COMMENT_ID, USER_ID)).thenReturn(new LikeStateResponse(2L, true));

        mockMvc.perform(post("/api/comments/" + COMMENT_ID + "/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.likeCount").value(2))
                .andExpect(jsonPath("$.data.liked").value(true));

        verify(commentService).like(COMMENT_ID, USER_ID);
    }

    @Test
    @DisplayName("取消评论点赞返回最新点赞状态")
    void shouldUnlikeComment() throws Exception {
        authenticate();
        when(commentService.unlike(COMMENT_ID, USER_ID)).thenReturn(new LikeStateResponse(1L, false));

        mockMvc.perform(delete("/api/comments/" + COMMENT_ID + "/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.liked").value(false));

        verify(commentService).unlike(COMMENT_ID, USER_ID);
    }
}
