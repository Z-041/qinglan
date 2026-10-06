package com.qinglan.review.controller;

import com.qinglan.common.web.ApiResponse;
import com.qinglan.review.dto.AdminStatsResponse;
import com.qinglan.review.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营看板接口。类级角色限定且默认拒绝，与审核工作台同口径。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    public ApiResponse<AdminStatsResponse> overview() {
        return ApiResponse.ok(adminStatsService.overview());
    }
}
