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
 * 运营看板接口。权限点写在方法级（P5 设计 7.1），类上不做任何放宽。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('STATS_VIEW')")
    public ApiResponse<AdminStatsResponse> overview() {
        return ApiResponse.ok(adminStatsService.overview());
    }
}
