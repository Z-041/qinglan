package com.qinglan.post.controller;

import com.qinglan.announcement.dto.PendingAnnouncementResponse;
import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.error.PreconditionRequiredException;
import com.qinglan.common.interaction.LikeStateResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.dto.PostDetailResponse;
import com.qinglan.post.dto.PostSort;
import com.qinglan.post.dto.PostSummaryResponse;
import com.qinglan.post.service.PostService;
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
import static org.mockito.ArgumentMatchers.argThat;
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
 * 帖子接口的参数校验与状态码映射（Web 层切片）。
 *
 * <p>只读接口对游客开放，写接口需要登录；鉴权由过滤器在完整上下文中决定，此处关闭过滤器，
 * 只校验 {@code CurrentUser} 抛出的 401、DTO 约束的 400、服务层透传的 403/404/428 等状态码。</p>
 */
@WebMvcTest(controllers = PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    private static final long USER_ID = 7L;
    private static final long POST_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

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

    private static PostDetailResponse detailResponse() {
        return new PostDetailResponse(
                POST_ID, 3L, "树洞", "请勿人身攻击", "有人一起自习吗", null, false,
                List.of(), "代号A", 0L, false, 0, true, null, false,
                Instant.parse("2026-10-07T00:00:00Z"));
    }

    @Test
    @DisplayName("发布帖子成功，作者取自登录态")
    void shouldCreatePost() throws Exception {
        authenticate();
        when(postService.create(eq(USER_ID), any())).thenReturn(detailResponse());

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"content\":\"有人一起自习吗\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.id").value(POST_ID));

        verify(postService).create(eq(USER_ID), any());
    }

    @Test
    @DisplayName("缺少话题返回 400")
    void shouldRejectMissingTopic() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"正文\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("正文为空返回 400，并给出校验消息")
    void shouldRejectBlankContent() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"))
                .andExpect(jsonPath("$.message").value("正文不能为空"));
    }

    @Test
    @DisplayName("未登录发布帖子返回 401")
    void shouldRejectAnonymousCreate() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"content\":\"正文\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("存在未读紧急公告时发帖返回 428，且响应体携带公告内容")
    void shouldReturn428WhenUrgentAnnouncementUnread() throws Exception {
        authenticate();
        PendingAnnouncementResponse pending = new PendingAnnouncementResponse(
                9L, "紧急通知", "请先阅读", AnnouncementLevel.URGENT, 5, true, true,
                Instant.parse("2026-10-07T00:00:00Z"));
        when(postService.create(eq(USER_ID), any()))
                .thenThrow(new PreconditionRequiredException(ErrorCode.PRECONDITION_REQUIRED, pending));

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"content\":\"正文\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("QL-428-001"))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.level").value("URGENT"));
    }

    @Test
    @DisplayName("信息流列表返回 200，游客访问时 viewer 的 userId 为空")
    void shouldListFeedForGuest() throws Exception {
        when(postService.listFeed(any(), any(), any(), any())).thenReturn(CursorPage.of(
                List.of(new PostSummaryResponse(POST_ID, 3L, "树洞", "有人一起自习吗", List.of(),
                        "代号A", 0L, false, 0, false, Instant.parse("2026-10-07T00:00:00Z"))),
                "1"));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.items[0].id").value(POST_ID))
                .andExpect(jsonPath("$.data.nextCursor").value("1"));

        verify(postService).listFeed(isNull(), eq(PostSort.LATEST), isNull(),
                argThat(viewer -> viewer.userId() == null));
    }

    @Test
    @DisplayName("未登录查看我的帖子返回 401")
    void shouldRejectAnonymousMine() throws Exception {
        mockMvc.perform(get("/api/posts/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("详情返回 200，登录态透传为已认证 viewer")
    void shouldReturnDetail() throws Exception {
        authenticate();
        when(postService.detail(eq(POST_ID), any())).thenReturn(detailResponse());

        mockMvc.perform(get("/api/posts/" + POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.id").value(POST_ID));

        verify(postService).detail(eq(POST_ID), argThat(viewer -> viewer.userId() == USER_ID));
    }

    @Test
    @DisplayName("帖子不存在或不可见返回 404")
    void shouldReturn404WhenPostNotFound() throws Exception {
        when(postService.detail(eq(POST_ID), any()))
                .thenThrow(new BizException(ErrorCode.POST_NOT_FOUND));

        mockMvc.perform(get("/api/posts/" + POST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-002"));
    }

    @Test
    @DisplayName("未登录点赞返回 401")
    void shouldRejectAnonymousLike() throws Exception {
        mockMvc.perform(post("/api/posts/" + POST_ID + "/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("点赞成功返回最新点赞状态")
    void shouldLikePost() throws Exception {
        authenticate();
        when(postService.like(POST_ID, USER_ID)).thenReturn(new LikeStateResponse(1L, true));

        mockMvc.perform(post("/api/posts/" + POST_ID + "/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.liked").value(true));

        verify(postService).like(POST_ID, USER_ID);
    }

    @Test
    @DisplayName("取消点赞成功返回最新点赞状态")
    void shouldUnlikePost() throws Exception {
        authenticate();
        when(postService.unlike(POST_ID, USER_ID)).thenReturn(new LikeStateResponse(0L, false));

        mockMvc.perform(delete("/api/posts/" + POST_ID + "/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.liked").value(false));

        verify(postService).unlike(POST_ID, USER_ID);
    }

    @Test
    @DisplayName("封禁用户发帖：服务层抛 403 原样返回")
    void shouldPropagateForbiddenFromService() throws Exception {
        authenticate();
        doThrow(new BizException(ErrorCode.USER_BANNED)).when(postService).create(eq(USER_ID), any());

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"content\":\"正文\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QL-403-002"));
    }
}
