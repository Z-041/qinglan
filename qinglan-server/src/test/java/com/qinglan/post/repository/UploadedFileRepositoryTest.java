package com.qinglan.post.repository;

import com.qinglan.post.entity.UploadedFile;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传登记表的持久层验证（真实 MySQL）：回收查询的过滤条件与 object_key 唯一约束。
 *
 * <p>用 JdbcTemplate 造数：{@code created_at} 由 {@code @PrePersist} 填当前时间，无法直接构造
 * 「过期」的行，只有显式 INSERT 才能给定历史时间。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UploadedFileRepositoryTest {

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("回收查询只命中「未绑定且已过保留期」：已绑定与未过期的都保留")
    void shouldSelectOnlyExpiredUnbound() {
        Instant old = Instant.parse("2026-01-01T00:00:00Z");
        insert(key('a'), null, old);              // 过期 + 未绑定 → 命中
        insert(key('b'), null, Instant.now());    // 刚上传 + 未绑定 → 不命中
        insert(key('c'), 42L, old);               // 过期 + 已绑定 → 不命中

        List<UploadedFile> found = uploadedFileRepository.findByBoundPostIdIsNullAndCreatedAtBefore(
                Instant.now().minus(Duration.ofDays(1)), PageRequest.of(0, 100));

        assertThat(found).extracting(UploadedFile::getObjectKey).containsExactly(key('a'));
    }

    @Test
    @DisplayName("object_key 唯一：同一对象不能被登记两次")
    void shouldEnforceUniqueObjectKey() {
        Instant now = Instant.now();
        insert(key('d'), null, now);

        assertThatThrownBy(() -> insert(key('d'), null, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(String objectKey, Long boundPostId, Instant createdAt) {
        Timestamp timestamp = Timestamp.from(createdAt);
        jdbcTemplate.update(
                "INSERT INTO uploaded_file (uploader_id, object_key, bound_post_id, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                7L, objectKey, boundPostId, timestamp, timestamp);
    }

    private static String key(char c) {
        return "2026/10/" + String.valueOf(c).repeat(32) + ".jpg";
    }
}
