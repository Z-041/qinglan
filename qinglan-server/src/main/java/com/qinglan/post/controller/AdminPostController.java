package com.qinglan.post.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.post.service.PostService;
import com.qinglan.user.dto.ReasonRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子的最小审核入口。
 *
 * <p>P1 只提供「放行 / 驳回」两个动作，用于把先审后发的链路跑通；P2 接入机审与举报队列后，
 * 这里会扩展为完整的审核工作台（下架、隐藏联系方式、批量处置等）。所有动作写审计日志。</p>
 */
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminPostController {

    private final PostService postService;

    @PostMapping("/{postId}/approve")
    public ApiResponse<Void> approve(@PathVariable Long postId) {
        postService.approve(postId, CurrentUser.requireUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{postId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long postId, @Valid @RequestBody ReasonRequest request) {
        postService.reject(postId, CurrentUser.requireUserId(), request.reason());
        return ApiResponse.ok();
    }
}
