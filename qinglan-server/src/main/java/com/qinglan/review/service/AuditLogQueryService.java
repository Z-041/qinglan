package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLog;
import com.qinglan.common.audit.AuditLogRepository;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CsvWriter;
import com.qinglan.common.web.CompositeCursor;
import com.qinglan.common.web.CompositeCursor.KeyKind;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAuditLogItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 审计日志查询（P5 设计 7.5）。
 *
 * <p>只读：本服务只提供分页查询，不提供任何修改入口——审计表「只增不改删」由仓储层的最小接口
 * 与本服务共同保证（不变量 #7）。跨模块聚合放在 {@code review}，与 {@code AdminStatsService}
 * 同一取向：它是管理端聚合的唯一去处，白名单零改动。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 导出审计动作时 {@code review_log.target_id} 的哨兵值（导出没有单一目标实体） */
    private static final long EXPORT_TARGET_ID = 0L;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    /**
     * 导出前置校验：超过 {@link CsvWriter#DEFAULT_ROW_LIMIT} 直接抛
     * {@link ErrorCode#EXPORT_ROW_LIMIT_EXCEEDED}，明确报错而非静默截断。
     *
     * <p><b>必须在触碰响应之前调用</b>：导出控制器会先把 {@code Content-Type} 设为 {@code text/csv}，
     * 若此时才抛错，全局异常处理无法把 JSON 错误体写进一个已预设为 CSV 的响应（会以
     * {@code HttpMessageNotWritableException} 收场，客户端拿到 500 而不是 400）。拆成独立的前置步骤后，
     * 校验失败时响应尚未被设置为 CSV，错误照常按 JSON 返回。</p>
     */
    @Transactional(readOnly = true)
    public void requireExportable(AuditAction action, Instant from, Instant to) {
        if (auditLogRepository.countAdminFiltered(action, from, to) > CsvWriter.DEFAULT_ROW_LIMIT) {
            throw new BizException(ErrorCode.EXPORT_ROW_LIMIT_EXCEEDED);
        }
    }

    /**
     * 按当前筛选条件流式导出 CSV（阶段 B）。调用方应先经 {@link #requireExportable} 校验。
     *
     * <p>两条硬要求：① <b>流式</b>逐行写出，不先拼成一个巨型字符串；② 导出这一敏感批量读取写一条
     * {@code DATA_EXPORT} 审计（不变量 #7 的意图范围），{@code reason} 记录资源与筛选条件摘要
     * （不含任何身份明文）。抓取条数已被前置校验约束在上限内。</p>
     *
     * <p>非只读事务：需要在同一次导出里写审计行。</p>
     */
    @Transactional
    public void exportCsv(AuditAction action, Instant from, Instant to, Long operatorId, OutputStream out)
            throws IOException {
        List<AuditLog> rows = auditLogRepository.findAdminPage(
                action, from, to, null, null, PageRequest.of(0, CsvWriter.DEFAULT_ROW_LIMIT));
        auditLogService.record(AuditTargetType.EXPORT, EXPORT_TARGET_ID, AuditAction.DATA_EXPORT, operatorId,
                "审计日志导出：action=%s from=%s to=%s rows=%d".formatted(action, from, to, rows.size()));
        writeCsv(rows, out);
    }

    private void writeCsv(List<AuditLog> rows, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(CsvWriter.UTF8_BOM);
        writer.write(CsvWriter.line(
                "id", "targetType", "targetId", "action", "operatorId", "reason", "createdAt"));
        writer.write("\n");
        for (AuditLog log : rows) {
            writer.write(CsvWriter.line(
                    log.getId(),
                    log.getTargetType(),
                    log.getTargetId(),
                    log.getAction(),
                    log.getOperatorId(),
                    log.getReason(),
                    log.getCreatedAt() == null ? "" : log.getCreatedAt().toString()));
            writer.write("\n");
        }
        writer.flush();
    }

    @Transactional(readOnly = true)
    public CursorPage<AdminAuditLogItemResponse> page(
            AuditAction action, Instant from, Instant to, String cursorRaw) {
        AuditLogCursor cursor = parseCursor(cursorRaw);
        // 多取一条判断是否还有下一页，避免额外 count 查询
        List<AuditLog> rows = auditLogRepository.findAdminPage(
                action, from, to, cursor.createdAt(), cursor.id(), PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = rows.size() > DEFAULT_PAGE_SIZE;
        List<AuditLog> page = hasMore ? List.copyOf(rows.subList(0, DEFAULT_PAGE_SIZE)) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        List<AdminAuditLogItemResponse> items = page.stream().map(AuditLogQueryService::toItem).toList();
        String nextCursor = hasMore ? formatCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    private static AdminAuditLogItemResponse toItem(AuditLog log) {
        return new AdminAuditLogItemResponse(
                log.getId(),
                log.getTargetType(),
                log.getTargetId(),
                log.getAction(),
                log.getOperatorId(),
                log.getReason(),
                log.getCreatedAt());
    }

    /**
     * 解析游标。游标对客户端不透明，只要求原样回传；格式为「审计微秒:日志 id」，
     * 两段必须齐全且为正数，缺段、错位或非数字一律按非法游标拒绝。
     */
    private AuditLogCursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AuditLogCursor(null, null);
        }
        long[] key = CompositeCursor.parse(raw, KeyKind.MICROS, KeyKind.POSITIVE_LONG);
        return new AuditLogCursor(CursorCodec.decode(key[0]), key[1]);
    }

    private String formatCursor(AuditLog log) {
        return CompositeCursor.format(CursorCodec.encode(log.getCreatedAt()), log.getId());
    }

    /** 审计查询游标：与 {@link AuditLogRepository#findAdminPage} 的排序键逐一对应 */
    private record AuditLogCursor(Instant createdAt, Long id) {
    }
}
