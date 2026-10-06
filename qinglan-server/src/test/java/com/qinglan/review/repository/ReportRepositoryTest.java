package com.qinglan.review.repository;

import com.qinglan.common.error.BizException;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 持久层测试：真实 MySQL（本机 qinglan_test），不 mock 数据库行为。
 *
 * <p>重点验证不变量 #4（举报不可刷量）的最终防线：唯一键 {@code uk_report_target_reporter}。
 * Mock 掉 repository 的单测测不到任何数据库约束，这类断言只能在真实库上做。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReportRepositoryTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("同一用户对同一目标重复举报被唯一键拒绝（不变量 #4）")
    void shouldRejectDuplicateReportFromSameReporter() {
        reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 1L, 100L, ReportReason.ADVERTISEMENT, null));

        assertThatThrownBy(() -> reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 1L, 100L, ReportReason.ABUSE, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同用户可分别举报同一目标")
    void shouldAllowDifferentReportersOnSameTarget() {
        reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 1L, 100L, ReportReason.FRAUD, null));
        reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 1L, 200L, ReportReason.FRAUD, null));

        assertThat(reportRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("同一用户可举报不同目标，唯一键不影响正常举报")
    void shouldAllowSameReporterOnDifferentTargets() {
        reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 1L, 100L, ReportReason.ILLEGAL, null));
        reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.COMMENT, 1L, 100L, ReportReason.ILLEGAL, null));
        reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 2L, 100L, ReportReason.ILLEGAL, null));

        assertThat(reportRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("提交后落 PENDING 并可回查，时间戳由基类填充")
    void shouldPersistPendingReport() {
        Report saved = reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.COMMENT, 7L, 100L, ReportReason.PRIVACY, "含他人手机号"));

        Report found = reportRepository
                .findByTargetTypeAndTargetIdAndReporterId(ReviewTargetType.COMMENT, 7L, 100L)
                .orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(found.getReasonCode()).isEqualTo(ReportReason.PRIVACY);
        assertThat(found.getDetail()).isEqualTo("含他人手机号");
        assertThat(found.getHandledAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("已处置的举报不允许再次处置（并发下两位管理员同时点同一目标）")
    void shouldRejectHandlingTwice() {
        Report report = reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 9L, 100L, ReportReason.OTHER, "说明"));
        report.dismiss(900L);

        assertThatThrownBy(() -> report.uphold(901L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("跨类型目标 id 相同且举报数、首报时间并列时，游标翻页不跳行：排序键含目标类型")
    void shouldPageAcrossTargetTypesWithSameTargetId() {
        Report postReport = reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.POST, 7L, 100L, ReportReason.ABUSE, null));
        Report commentReport = reportRepository.saveAndFlush(
                Report.submit(ReviewTargetType.COMMENT, 7L, 200L, ReportReason.ABUSE, null));
        // 直接把首报时间抹平，构造「举报数与首报时间都并列、只剩 target_id 相同」的极端并列
        Instant same = Instant.parse("2026-10-06T00:00:00Z");
        jdbcTemplate.update("UPDATE report SET created_at = ? WHERE id IN (?, ?)",
                Timestamp.from(same), postReport.getId(), commentReport.getId());

        // 首页只取一行，拿到该行的复合游标
        List<ReviewQueueRow> first = reportRepository.findQueueAggregates(
                ReportStatus.PENDING, Long.MAX_VALUE, Instant.EPOCH, Long.MAX_VALUE, Integer.MAX_VALUE,
                PageRequest.of(0, 1));
        assertThat(first).hasSize(1);
        ReviewQueueRow head = first.get(0);

        // 用该行做游标取下一页：若排序键缺目标类型，另一类型的那行会被判为「不在游标之后」而丢掉
        List<ReviewQueueRow> second = reportRepository.findQueueAggregates(
                ReportStatus.PENDING, head.reportCount(), head.firstReportedAt(), head.targetId(),
                typeRank(head.targetType()), PageRequest.of(0, 10));

        assertThat(second).hasSize(1);
        assertThat(second.get(0).targetId()).isEqualTo(7L);
        assertThat(second.get(0).targetType()).isNotEqualTo(head.targetType());
    }

    /** 与仓储查询里的 case 表达式保持同一套档位：POST=0、COMMENT=1；类型为 int 以匹配 case 推断 */
    private static int typeRank(ReviewTargetType targetType) {
        return targetType == ReviewTargetType.POST ? 0 : 1;
    }
}
