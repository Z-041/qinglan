package com.qinglan.comment.controller;

import com.qinglan.comment.dto.CommentResponse;
import com.qinglan.comment.dto.CreateCommentRequest;
import com.qinglan.comment.service.CommentService;
import com.qinglan.common.interaction.LikeStateResponse;
import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评论接口。路径横跨两个资源（帖子下的评论、评论自身），故不放在类级 {@code @RequestMapping} 上。
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 评论列表（一级 + 其回复），游客可读 */
    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<List<CommentResponse>> list(@PathVariable Long postId) {
        return ApiResponse.ok(commentService.list(postId, CurrentUser.viewer()));
    }

    /** 发表评论；parentId 非空即回复某条一级评论 */
    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<CommentResponse> create(@PathVariable Long postId,
                                               @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.ok(commentService.create(postId, CurrentUser.requireUserId(), request));
    }

    /** 删除自己的评论 */
    @DeleteMapping("/api/comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long commentId) {
        commentService.delete(commentId, CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/api/comments/{commentId}/like")
    public ApiResponse<LikeStateResponse> like(@PathVariable Long commentId) {
        return ApiResponse.ok(commentService.like(commentId, CurrentUser.requireUserId()));
    }

    @DeleteMapping("/api/comments/{commentId}/like")
    public ApiResponse<LikeStateResponse> unlike(@PathVariable Long commentId) {
        return ApiResponse.ok(commentService.unlike(commentId, CurrentUser.requireUserId()));
    }
}
