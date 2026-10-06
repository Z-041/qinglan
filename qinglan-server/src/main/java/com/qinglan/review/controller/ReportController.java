package com.qinglan.review.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.review.dto.CreateReportRequest;
import com.qinglan.review.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
