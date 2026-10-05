package com.qinglan.user.repository;

import com.qinglan.user.entity.User;
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
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

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
}
