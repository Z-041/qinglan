package com.qinglan.post.repository;

import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

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

    @Autowired
    private PostRepository postRepository;

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

    private Long savePendingPost() {
        Post post = Post.draft(1L, 1L, "测试正文", null);
        post.submit(RiskLevel.LOW);
        return postRepository.saveAndFlush(post).getId();
    }

    private int reportCountOf(Long postId) {
        return postRepository.findById(postId).orElseThrow().getReportCount();
    }
}
