package com.qinglan.post.repository;

import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.RiskLevel;
import com.qinglan.post.entity.ReviewState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持久层测试：真实 MySQL（本机 qinglan_test），不 mock 数据库行为。
 *
 * <p>计数类的列必须走原子 UPDATE：实体读-改-写在并发下必丢更新，且 mock 掉 repository
 * 的单测完全测不出来。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PostRepositoryTest {

    /** 与看板 pendingPosts 同口径（管理端待审队列用它） */
    private static final List<PostStatus> PENDING_STATUSES =
            List.of(PostStatus.PENDING, PostStatus.REVIEWING);

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("待处理举报数原子自增，并回查读到新值")
    void shouldIncreaseReportCount() {
        Long postId = savePendingPost();

        postRepository.adjustReportCount(postId, 1);
        postRepository.adjustReportCount(postId, 1);

        assertThat(reportCountOf(postId)).isEqualTo(2);
    }

    @Test
    @DisplayName("举报数减到负数时归零，不会出现负计数")
    void shouldClampReportCountAtZero() {
        Long postId = savePendingPost();
        postRepository.adjustReportCount(postId, 2);

        postRepository.adjustReportCount(postId, -5);

        assertThat(reportCountOf(postId)).isZero();
    }

    @Test
    @DisplayName("待审帖子键集分页：按 created_at/id 升序翻完，且只出 PENDING 与 REVIEWING")
    void shouldPagePendingPostsByKeyset() {
        Long pendingOne = createPostAt("2026-01-01T00:00:00Z", PostStatus.PENDING);
        Long reviewing = createPostAt("2026-01-02T00:00:00Z", PostStatus.REVIEWING);
        Long approved = createPostAt("2026-01-03T00:00:00Z", PostStatus.APPROVED);
        Long pendingTwo = createPostAt("2026-01-04T00:00:00Z", PostStatus.PENDING);

        List<Post> firstPage = postRepository.findPendingPage(
                PENDING_STATUSES, null, null, PageRequest.of(0, 2));

        // 已通过的帖子不参与分页，所以首页是「最早的两条待审」而不是「最早的两条帖子」
        assertThat(firstPage).extracting(Post::getId).containsExactly(pendingOne, reviewing);

        Post boundary = firstPage.get(1);
        List<Post> secondPage = postRepository.findPendingPage(PENDING_STATUSES,
                boundary.getCreatedAt(), boundary.getId(), PageRequest.of(0, 2));

        assertThat(secondPage).extracting(Post::getId).containsExactly(pendingTwo);
        assertThat(secondPage).extracting(Post::getId).doesNotContain(approved);
    }

    private Long savePendingPost() {
        Post post = Post.draft(1L, 1L, "测试正文", null);
        post.submit(RiskLevel.LOW);
        return postRepository.saveAndFlush(post).getId();
    }

    /**
     * 落一条指定状态、指定 {@code created_at} 的帖子。
     *
     * <p>{@code created_at} 由 {@code @PrePersist} 填当前时刻，要断言分页顺序只能落库后回写；
     * 回写后必须 {@code clear()}，否则再查会命中一级缓存里的旧值（与 {@code AppealRepositoryTest}
     * 同一手法）。</p>
     */
    private Long createPostAt(String isoInstant, PostStatus status) {
        Post post = Post.draft(1L, 1L, "测试正文", null);
        post.submit(RiskLevel.LOW);
        switch (status) {
            case REVIEWING -> post.transitionTo(new ReviewState.Reviewing(9L, Instant.now()));
            case APPROVED -> {
                post.transitionTo(new ReviewState.Reviewing(9L, Instant.now()));
                post.transitionTo(new ReviewState.Approved(9L, Instant.now()));
            }
            default -> {
                // PENDING：submit 之后不再迁移
            }
        }
        Post saved = postRepository.saveAndFlush(post);
        jdbcTemplate.update("update post set created_at = ? where id = ?",
                Timestamp.from(Instant.parse(isoInstant)), saved.getId());
        entityManager.clear();
        return saved.getId();
    }

    private int reportCountOf(Long postId) {
        return postRepository.findById(postId).orElseThrow().getReportCount();
    }
}
