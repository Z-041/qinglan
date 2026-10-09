package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLog;
import com.qinglan.common.audit.AuditLogRepository;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.review.dto.AdminAuditLogItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 审计日志查询的单元测试（P5 设计 7.5）。
 *
 * <p>盯四件事：分页键完全交给数据库、游标非法即拒绝、**不变量 #3——出参不含任何身份字段**、
 * 筛选条件原样透传。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    private static final Instant BASE = Instant.parse("2026-10-08T00:00:00Z");

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditLogQueryService auditLogQueryService;

    @Test
    @DisplayName("没有数据时返回空页与空游标，不产生额外查询")
    void shouldReturnEmptyPage() {
        stubFirstPage(List.of());

        CursorPage<AdminAuditLogItemResponse> page = auditLogQueryService.page(null, null, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("装配一行：动作、目标、操作人、理由与时间原样带出")
    void shouldAssembleItem() {
        stubFirstPage(List.of(log(1L, AuditAction.POST_TAKEDOWN, 7L, 99L, "ABUSE: 人身攻击", BASE)));

        AdminAuditLogItemResponse item = auditLogQueryService.page(null, null, null, null).items().get(0);

        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.targetType()).isEqualTo(AuditTargetType.POST);
        assertThat(item.targetId()).isEqualTo(7L);
        assertThat(item.action()).isEqualTo(AuditAction.POST_TAKEDOWN);
        assertThat(item.operatorId()).isEqualTo(99L);
        assertThat(item.reason()).isEqualTo("ABUSE: 人身攻击");
        assertThat(item.createdAt()).isEqualTo(BASE);
    }

    @Test
    @DisplayName("多取一条判断还有下一页，游标为「审计微秒:日志 id」")
    void shouldEmitOpaqueCursorForNextPage() {
        List<AuditLog> rows = IntStream.range(0, 21)
                .mapToObj(i -> log(100L - i, AuditAction.BAN, 7L, 99L, null, BASE.plusSeconds(i)))
                .toList();
        stubFirstPage(rows);

        CursorPage<AdminAuditLogItemResponse> page = auditLogQueryService.page(null, null, null, null);

        assertThat(page.items()).hasSize(20);
        assertThat(page.nextCursor()).isEqualTo(BASE.plusSeconds(19).getEpochSecond() * 1_000_000L + ":81");
    }

    @Test
    @DisplayName("筛选条件与解析后的游标原样交给仓储")
    void shouldPassFiltersAndCursorToRepository() {
        Instant from = BASE.minusSeconds(3600);
        Instant to = BASE.plusSeconds(3600);
        long micros = BASE.plusSeconds(5).getEpochSecond() * 1_000_000L;
        when(auditLogRepository.findAdminPage(eq(AuditAction.ROLE_GRANT), eq(from), eq(to),
                eq(BASE.plusSeconds(5)), eq(7L), any(Pageable.class))).thenReturn(List.of());

        auditLogQueryService.page(AuditAction.ROLE_GRANT, from, to, micros + ":7");

        verify(auditLogRepository).findAdminPage(AuditAction.ROLE_GRANT, from, to,
                BASE.plusSeconds(5), 7L, PageRequest.of(0, 21));
    }

    @Test
    @DisplayName("缺段、非数字、非正数的游标一律按非法游标拒绝，且不触碰仓储")
    void shouldRejectMalformedCursor() {
        for (String raw : List.of("not-a-cursor", "123", "0:7", "1:0")) {
            assertThatThrownBy(() -> auditLogQueryService.page(null, null, null, raw))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).errorCode())
                    .isEqualTo(ErrorCode.CURSOR_INVALID);
        }
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("不变量 #3：审计查询出参不含任何身份字段（超管「全见」也不含明文身份）")
    void shouldNotExposeIdentityInAuditItem() {
        Set<String> componentNames = Arrays.stream(AdminAuditLogItemResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(componentNames).containsExactlyInAnyOrder(
                "id", "targetType", "targetId", "action", "operatorId", "reason", "createdAt");
        assertThat(componentNames).doesNotContain(
                "email", "studentId", "studentIdEnc", "emailHash", "anonymousCode", "anonymousSalt");
    }

    // ------------------------------------------------------------------ CSV 导出（阶段 B）

    @Test
    @DisplayName("导出：UTF-8 BOM + 表头 + 逐行数据，且写一条 DATA_EXPORT 审计")
    void shouldExportCsvWithBomAndAudit() throws Exception {
        when(auditLogRepository.findAdminPage(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(log(1L, AuditAction.POST_TAKEDOWN, 7L, 99L, "=恶意公式", BASE)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        auditLogQueryService.exportCsv(null, null, null, 99L, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("id,targetType,targetId,action,operatorId,reason,createdAt");
        // CSV 注入防护：以 = 开头的理由被前置单引号
        assertThat(csv).contains("'=恶意公式");
        verify(auditLogService).record(eq(AuditTargetType.EXPORT), eq(0L), eq(AuditAction.DATA_EXPORT),
                eq(99L), any());
    }

    @Test
    @DisplayName("导出前置校验：行数超上限时明确报错（不静默截断），且不写审计")
    void shouldRejectExportOverLimit() {
        when(auditLogRepository.countAdminFiltered(any(), any(), any())).thenReturn(5001L);

        assertThatThrownBy(() -> auditLogQueryService.requireExportable(null, null, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.EXPORT_ROW_LIMIT_EXCEEDED);

        verifyNoInteractions(auditLogService);
    }

    // ------------------------------------------------------------------ 辅助

    private void stubFirstPage(List<AuditLog> rows) {
        when(auditLogRepository.findAdminPage(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(rows);
    }

    private static AuditLog log(Long id, AuditAction action, Long targetId, Long operatorId,
                                String reason, Instant createdAt) {
        AuditLog log = new AuditLog(AuditTargetType.POST, targetId, action, operatorId, reason);
        ReflectionTestUtils.setField(log, "id", id);
        ReflectionTestUtils.setField(log, "createdAt", createdAt);
        return log;
    }
}
