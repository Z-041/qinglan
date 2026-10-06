package com.qinglan.review.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.review.dto.SubmitAppealRequest;
import com.qinglan.review.service.AppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端申诉入口。
 *
 * <p>申诉人取自登录态，服务端再校验其确为该帖作者；请求体只含目标帖子与理由。</p>
 */
@RestController
@RequestMapping("/api/appeals")
@RequiredArgsConstructor
public class AppealController {

    private final AppealService appealService;

    @PostMapping
    public ApiResponse<Void> submit(@Valid @RequestBody SubmitAppealRequest request) {
        appealService.submit(CurrentUser.requireUserId(), request.postId(), request.reason());
        return ApiResponse.ok();
    }
}
