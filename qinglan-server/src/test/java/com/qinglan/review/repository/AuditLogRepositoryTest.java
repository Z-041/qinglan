package com.qinglan.review.repository;

import com.qinglan.review.entity.AuditAction;
import com.qinglan.review.entity.AuditLog;
import com.qinglan.review.entity.AuditTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不变量 #7：审计日志只增不改删。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("审计日志可写入并可回查")
    void shouldPersistAuditLog() {
        AuditLog saved = auditLogRepository.save(
                new AuditLog(AuditTargetType.USER, 42L, AuditAction.BAN, 99L, "人身攻击"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getTargetId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("仓储接口不暴露任何删除/更新能力")
    void shouldNotExposeMutatingOperations() {
        Set<String> mutatingMethods = Arrays.stream(AuditLogRepository.class.getMethods())
                .filter(method -> method.getDeclaringClass() != Object.class)
                .map(Method::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .filter(name -> name.contains("delete") || name.contains("update") || name.contains("remove"))
                .collect(Collectors.toSet());

        assertThat(mutatingMethods).isEmpty();
    }

    @Test
    @DisplayName("审计日志实体没有 setter，写入后无法被改写")
    void shouldHaveNoSetters() {
        Set<String> setters = Arrays.stream(AuditLog.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .collect(Collectors.toSet());

        assertThat(setters).isEmpty();
    }
}
