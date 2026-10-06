package com.qinglan.review.repository;

import com.qinglan.review.entity.Appeal;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.entity.ReviewTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 持久层测试：真实 MySQL（本机 qinglan_test），不 mock 数据库行为。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AppealRepositoryTest {

    @Autowired
    private AppealRepository appealRepository;

    @Test
    @DisplayName("同一目标只能申诉一次")
    void shouldRejectSecondAppealOnSameTarget() {
        appealRepository.saveAndFlush(Appeal.submit(ReviewTargetType.POST, 1L, 100L, "处理有误"));

        assertThatThrownBy(() -> appealRepository.saveAndFlush(
                Appeal.submit(ReviewTargetType.POST, 1L, 100L, "再次申诉")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同目标可各自申诉")
    void shouldAllowAppealPerTarget() {
        appealRepository.saveAndFlush(Appeal.submit(ReviewTargetType.POST, 1L, 100L, "处理有误"));
        appealRepository.saveAndFlush(Appeal.submit(ReviewTargetType.POST, 2L, 100L, "处理有误"));

        assertThat(appealRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("申诉初始为待复审且未恢复")
    void shouldPersistPendingAppeal() {
        appealRepository.saveAndFlush(Appeal.submit(ReviewTargetType.POST, 5L, 100L, "证据不足"));

        Appeal found = appealRepository.findByTargetTypeAndTargetId(ReviewTargetType.POST, 5L).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(AppealStatus.PENDING);
        assertThat(found.isRestored()).isFalse();
        assertThat(found.getReviewedBy()).isNull();
        assertThat(found.getReviewedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("复审结论记录复审人并置恢复标记")
    void shouldRecordReviewOutcome() {
        Appeal appeal = appealRepository.saveAndFlush(Appeal.submit(ReviewTargetType.POST, 6L, 100L, "误判"));
        appeal.accept(900L);

        Appeal found = appealRepository.findByTargetTypeAndTargetId(ReviewTargetType.POST, 6L).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(AppealStatus.ACCEPTED);
        assertThat(found.isRestored()).isTrue();
        assertThat(found.getReviewedBy()).isEqualTo(900L);
        assertThat(found.getReviewedAt()).isNotNull();
    }
}
