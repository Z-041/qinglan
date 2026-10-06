package com.qinglan.user.repository;

import com.qinglan.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 持久层测试：真实 MySQL（本机 qinglan_test），不 mock 数据库行为。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("邮箱哈希唯一约束生效")
    void shouldEnforceUniqueEmailHash() {
        userRepository.saveAndFlush(newUser("dup-email-hash", "student-hash-1"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("dup-email-hash", "student-hash-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("学号哈希唯一约束生效，保证一人一学号")
    void shouldEnforceUniqueStudentIdHash() {
        userRepository.saveAndFlush(newUser("email-hash-1", "dup-student-hash"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("email-hash-2", "dup-student-hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("可按邮箱哈希精确查询用户")
    void shouldFindByEmailHash() {
        userRepository.saveAndFlush(newUser("lookup-email-hash", "lookup-student-hash"));

        User found = userRepository.findByEmailHash("lookup-email-hash").orElseThrow();

        assertThat(found.getStudentIdEnc()).isEqualTo("encrypted-student-id");
        assertThat(found.getCreditScore()).isEqualTo(User.INITIAL_CREDIT_SCORE);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    private User newUser(String emailHash, String studentIdHash) {
        return User.register(emailHash, studentIdHash, "encrypted-student-id", "a".repeat(32));
    }

    @Test
    @DisplayName("按注册时间倒序返回，游标翻页不重不漏")
    void shouldPageByCreatedAtCursor() {
        Long first = createUserAt("cursor-email-1", "cursor-student-1", "2026-01-01T00:00:00Z");
        Long second = createUserAt("cursor-email-2", "cursor-student-2", "2026-01-02T00:00:00Z");
        Long third = createUserAt("cursor-email-3", "cursor-student-3", "2026-01-03T00:00:00Z");

        List<User> page1 = userRepository.findAdminPage(
                null, null, null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 2));
        assertThat(page1).extracting(User::getId).containsExactly(third, second);

        User last = page1.get(page1.size() - 1);
        List<User> page2 = userRepository.findAdminPage(
                null, null, null, null, null, null, last.getCreatedAt(), last.getId(),
                org.springframework.data.domain.PageRequest.of(0, 2));
        assertThat(page2).extracting(User::getId).containsExactly(first);
    }

    @Test
    @DisplayName("按状态与信用分区间筛选")
    void shouldFilterByStatusAndCredit() {
        Long active = createUserAt("filter-email-1", "filter-student-1", "2026-02-01T00:00:00Z");
        Long banned = createUserAt("filter-email-2", "filter-student-2", "2026-02-02T00:00:00Z");
        jdbcTemplate.update("update user set status = 'BANNED', credit_score = 30 where id = ?", banned);
        entityManager.clear();

        List<User> onlyBanned = userRepository.findAdminPage(
                com.qinglan.user.entity.UserStatus.BANNED, null, null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(onlyBanned).extracting(User::getId).containsExactly(banned);

        List<User> lowCredit = userRepository.findAdminPage(
                null, null, 0, 50, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(lowCredit).extracting(User::getId).containsExactly(banned);
        assertThat(active).isNotNull();
    }

    private Long createUserAt(String emailHash, String studentIdHash, String isoInstant) {
        User saved = userRepository.saveAndFlush(newUser(emailHash, studentIdHash));
        jdbcTemplate.update("update user set created_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse(isoInstant)), saved.getId());
        // JdbcTemplate 绕过 JPA，一级缓存里的实体仍是旧时间。不清空的话，下面查回来的是内存中
        // 的旧 createdAt（Hibernate 对已在持久化上下文中的 id 不会用查询结果覆盖），游标会算错。
        entityManager.clear();
        return saved.getId();
    }
}
