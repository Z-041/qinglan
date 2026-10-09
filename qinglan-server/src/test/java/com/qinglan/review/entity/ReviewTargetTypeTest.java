package com.qinglan.review.entity;

import com.qinglan.common.audit.AuditTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审核目标类型 → 审计目标类型的映射。
 *
 * <p>此前三个 Service 各写一份 switch，现收敛到 {@link ReviewTargetType#auditTarget()} 一处。</p>
 */
class ReviewTargetTypeTest {

    @Test
    @DisplayName("两个取值分别映射到同名的审计目标类型")
    void mapsToAuditTarget() {
        assertThat(ReviewTargetType.POST.auditTarget()).isEqualTo(AuditTargetType.POST);
        assertThat(ReviewTargetType.COMMENT.auditTarget()).isEqualTo(AuditTargetType.COMMENT);
    }
}
