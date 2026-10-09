package com.qinglan.review.controller;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.common.web.CsvDownload;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAuditLogItemResponse;
import com.qinglan.review.service.AuditLogQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志查询接口（P5 设计 7.5）。
 *
 * <p>仅超级管理员（{@code AUDIT_READ}）：审计里记着「谁在什么时候对什么做了什么」，
 * 是治理动作的完整轨迹，不该对普通管理员开放——否则委员之间能看到彼此的全部处置记录，
 * 与「投票只可见汇总」的取向相冲突。</p>
 *
 * <p>只读接口不设写保护：不变量 #7 要求审计只增不改删，本控制器自然也不提供任何写入口。</p>
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    /** 游标分页 + 可选按动作 / 时间窗过滤；无过滤条件时按时间倒序返回最近的一页 */
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ApiResponse<CursorPage<AdminAuditLogItemResponse>> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(auditLogQueryService.page(action, from, to, cursor));
    }

    /**
     * 按当前筛选条件流式导出 CSV（阶段 B，权限同查询）。
     *
     * <p>参数与列表接口一致（action / from / to）。导出内容由 {@link AdminAuditLogItemResponse}
     * 的字段集决定，不含任何身份明文；导出动作本身写 {@code DATA_EXPORT} 审计（不变量 #7）。</p>
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public void export(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            HttpServletResponse response) throws IOException {
        // 先校验行数上限，再设置 CSV 响应头：校验失败时响应尚未被设为 text/csv，错误能按 JSON 返回
        auditLogQueryService.requireExportable(action, from, to);
        CsvDownload.prepare(response, "audit-logs");
        auditLogQueryService.exportCsv(action, from, to, CurrentUser.requireUserId(), response.getOutputStream());
    }
}
