package com.qinglan.post.controller;

import com.qinglan.common.interaction.LikeStateResponse;
import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.post.dto.PostDetailResponse;
import com.qinglan.post.dto.PostSummaryResponse;
import com.qinglan.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子接口。
 *
 * <p>列表与详情对游客开放（游客是只读角色），通过 {@link CurrentUser#viewer()} 区分游客与
 * 认证学生，从而决定联系方式是否脱敏；发布与点赞仍需登录，由安全配置拦截。</p>
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** 发布，落 Pending 待审（红线 2：主帖先审后发） */
    @PostMapping
    public ApiResponse<PostDetailResponse> create(@Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(postService.create(CurrentUser.requireUserId(), request));
    }

    /** 信息流，游标分页（页大小由服务端固定）；topic 为空表示全部话题 */
    @GetMapping
    public ApiResponse<CursorPage<PostSummaryResponse>> list(
            @RequestParam(required = false) Long topic,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(postService.listFeed(topic, cursor, CurrentUser.viewer()));
    }

    /** 详情，含作者匿名代号；联系方式按 viewer 身份在服务端脱敏（不变量 #2） */
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> detail(@PathVariable Long postId) {
        return ApiResponse.ok(postService.detail(postId, CurrentUser.viewer()));
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<LikeStateResponse> like(@PathVariable Long postId) {
        return ApiResponse.ok(postService.like(postId, CurrentUser.requireUserId()));
    }

    @DeleteMapping("/{postId}/like")
    public ApiResponse<LikeStateResponse> unlike(@PathVariable Long postId) {
        return ApiResponse.ok(postService.unlike(postId, CurrentUser.requireUserId()));
    }
}
