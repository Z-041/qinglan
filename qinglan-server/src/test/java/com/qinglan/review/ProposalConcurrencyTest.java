package com.qinglan.review;

import com.qinglan.common.error.BizException;
import com.qinglan.post.dto.CreatePostRequest;
import com.qinglan.post.service.PostService;
import com.qinglan.review.dto.CreateProposalRequest;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.VoteDecision;
import com.qinglan.review.service.ProposalService;
import com.qinglan.review.service.ReviewService;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
import com.qinglan.common.crypto.CryptoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表决的并发保证（P5 二期，设计 10.2 步 5）：多线程同时投「最后一票」，处置只能执行一次。
 *
 * <p><b>为什么不用 {@code @Transactional}</b>：并发要成立，两条投票必须各自跑在自己的事务里，
 * 而测试事务内的未提交数据对其它线程不可见。所以本类刻意不加 {@code @Transactional}，
 * 代价是数据会真实落库，必须在 {@link #cleanUp()} 里按本次创建的 id 精确删除——
 * 尤其要删用户：{@code countCommittee()} 是全局计数，残留委员会成员会让其它用例的分母快照失真。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ProposalConcurrencyTest {

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> userIds = new ArrayList<>();
    private Long postId;

    @Test
    @DisplayName("两人同时投最后一票：处置只执行一次，审计只有一条，另一人得到 409 语义")
    void shouldExecuteDisposalOnlyOnceUnderConcurrentVotes() throws Exception {
        User author = createStudent("author@qinglan.edu.cn", "2023011001");
        User admin1 = createAdmin("a1@qinglan.edu.cn", "2023012001");
        User admin2 = createAdmin("a2@qinglan.edu.cn", "2023012002");
        User admin3 = createAdmin("a3@qinglan.edu.cn", "2023012003");

        // 在册 3 人 → needed = 2；先投 1 票，剩下两人同时投 → 只有一人能成为「第 2 票」
        postId = publishAndApprove(author.getId(), admin1.getId());
        Long proposalId = proposalService.create(new CreateProposalRequest(
                ReviewTargetType.POST, postId, ProposalAction.TAKE_DOWN, ReportReason.ABUSE, "人身攻击"),
                admin1.getId());
        proposalService.vote(proposalId, VoteDecision.AGREE, admin1.getId());

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes;
        try {
            Future<String> first = pool.submit(() -> voteAfterBarrier(barrier, proposalId, admin2.getId()));
            Future<String> second = pool.submit(() -> voteAfterBarrier(barrier, proposalId, admin3.getId()));
            outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        // 一个成功、一个因提案已结案被拒；不可能两个都成功
        assertThat(outcomes).containsExactlyInAnyOrder("OK", "ILLEGAL_REVIEW_TRANSITION");
        assertThat(postStatus(postId)).isEqualTo("TAKEN_DOWN");
        // 处置只发生一次（提案行锁把两次投票串行化了），审计也只落一条
        assertThat(count("SELECT COUNT(*) FROM review_log WHERE target_type = 'POST' AND target_id = ? "
                + "AND action = 'POST_TAKEDOWN'", postId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM review_log WHERE target_type = 'POST' AND target_id = ? "
                + "AND action = 'PROPOSAL_RESOLVE'", postId)).isEqualTo(1);
        assertThat(countString("SELECT status FROM review_proposal WHERE id = ?", proposalId))
                .isEqualTo("APPROVED");
    }

    /** 两个线程在栅栏处对齐后同时投票，返回结果标记；业务异常按其错误码归类 */
    private String voteAfterBarrier(CyclicBarrier barrier, Long proposalId, Long voterId) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("线程未能在栅栏处对齐", ex);
        }
        try {
            proposalService.vote(proposalId, VoteDecision.AGREE, voterId);
            return "OK";
        } catch (BizException ex) {
            return ex.errorCode().name();
        }
    }

    // ------------------------------------------------------------------ 造数与断言

    private long publishAndApprove(Long authorId, Long operatorId) {
        Long topicId = jdbcTemplate.queryForObject("SELECT id FROM topic WHERE name = ?", Long.class, "二手");
        long id = postService.create(authorId,
                new CreatePostRequest(topicId, "出一辆九成新自行车", "微信 qinglan_demo", List.of())).id();
        reviewService.approve(id, operatorId);
        return id;
    }

    private String postStatus(long id) {
        return countString("SELECT status FROM post WHERE id = ?", id);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String countString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private User createStudent(String email, String studentId) {
        User user = userService.createVerifiedStudent(cryptoService.hashEmail(email), studentId);
        userIds.add(user.getId());
        return user;
    }

    private User createAdmin(String email, String studentId) {
        User user = createStudent(email, studentId);
        // 必须在显式事务里改角色：本类没有测试事务，userService.requireById 返回的实体在方法返回后
        // 就脱离了持久化上下文，直接改它会静默不落库（P5 D9 之后「令牌角色 = 数据库角色」会因此失败）。
        // 不用 UserService.changeRole：它要校验唯一超管行锁与 6 人上限，超出本用例的造数范围。
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userService.requireById(user.getId()).changeRole(UserRole.ADMIN));
        return user;
    }

    // ------------------------------------------------------------------ 清理

    /**
     * 本类不加 {@code @Transactional}，数据会真实落库，必须按 id 精确删除。
     * 顺序无所谓（迁移未建外键），但用户必须先于其它表之后删也无妨——这里统一按表清理。
     */
    @AfterEach
    void cleanUp() {
        if (postId != null) {
            jdbcTemplate.update("DELETE FROM review_vote WHERE proposal_id IN "
                    + "(SELECT id FROM review_proposal WHERE target_type = 'POST' AND target_id = ?)", postId);
            jdbcTemplate.update("DELETE FROM review_proposal WHERE target_type = 'POST' AND target_id = ?", postId);
            jdbcTemplate.update("DELETE FROM review_log WHERE target_type = 'POST' AND target_id = ?", postId);
            jdbcTemplate.update("DELETE FROM notification WHERE link = ?", "/posts/" + postId);
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
        }
        if (!userIds.isEmpty()) {
            String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbcTemplate.update("DELETE FROM review_vote WHERE voter_id IN (" + ids + ")");
            jdbcTemplate.update("DELETE FROM review_proposal WHERE proposer_id IN (" + ids + ")");
            jdbcTemplate.update("DELETE FROM review_log WHERE operator_id IN (" + ids + ")");
            jdbcTemplate.update("DELETE FROM notification WHERE user_id IN (" + ids + ")");
            jdbcTemplate.update("DELETE FROM `user` WHERE id IN (" + ids + ")");
        }
    }
}
