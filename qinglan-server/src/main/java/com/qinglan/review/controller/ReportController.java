package com.qinglan.review.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.review.dto.CreateReportRequest;
import com.qinglan.review.dto.MyReportStatusResponse;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端举报入口。
 *
 * <p>举报人取自登录态，请求体不含任何身份字段。</p>
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CreateReportRequest request) {
        reportService.report(CurrentUser.requireUserId(), request);
        return ApiResponse.ok();
    }

    /**
     * 当前登录用户在某个目标上的举报状态。
     *
     * <p>需登录（{@code /api/reports/**} 不在 {@code SecurityConfig} 的游客放行名单里）；
     * 举报人取自登录态，故不会返回任何他人举报信息（不变量 #1）。</p>
     */
    @GetMapping("/mine")
    public ApiResponse<MyReportStatusResponse> mine(
            @RequestParam ReviewTargetType targetType,
            @RequestParam Long targetId) {
        return ApiResponse.ok(
                reportService.myReportStatus(CurrentUser.requireUserId(), targetType, targetId));
    }
}
