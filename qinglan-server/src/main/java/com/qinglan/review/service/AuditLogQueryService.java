package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLog;
import com.qinglan.common.audit.AuditLogRepository;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAuditLogItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final String KEY_SEPARATOR = ":";
    /** 游标段数：审计微秒 : 日志 id */
    private static final int CURSOR_SEGMENTS = 2;

    private final AuditLogRepository auditLogRepository;

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
        String[] parts = raw.split(KEY_SEPARATOR);
        if (parts.length != CURSOR_SEGMENTS) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
        try {
            long micros = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (micros <= 0 || id <= 0) {
                throw new NumberFormatException("游标各段不合法");
            }
            return new AuditLogCursor(CursorCodec.decode(micros), id);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatCursor(AuditLog log) {
        return CursorCodec.encode(log.getCreatedAt()) + KEY_SEPARATOR + log.getId();
    }

    /** 审计查询游标：与 {@link AuditLogRepository#findAdminPage} 的排序键逐一对应 */
    private record AuditLogCursor(Instant createdAt, Long id) {
    }
}
