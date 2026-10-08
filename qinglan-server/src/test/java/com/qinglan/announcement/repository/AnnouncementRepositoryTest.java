package com.qinglan.announcement.repository;

import com.qinglan.announcement.entity.Announcement;
import com.qinglan.announcement.entity.AnnouncementLevel;
import com.qinglan.announcement.entity.AnnouncementRead;
import com.qinglan.announcement.entity.AnnouncementStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公告持久层测试：真实 MySQL（本机 qinglan_test），不 mock 数据库行为。
 *
 * <p>重点验证 {@code findPending} 的显式优先级排序、级别过滤，以及「登录用户只看账号维度、游客只看设备维度」
 * 的排除逻辑——这段 JPQL 的 enum 参数 CASE 排序与可空参数排除只有真跑才能确认。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AnnouncementRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementReadRepository announcementReadRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("待读按级别优先级取一条：URGENT 优先于 NORMAL")
    void shouldOrderPendingByLevelPriority() {
        publish("普通公告", AnnouncementLevel.NORMAL, NOW.minusSeconds(10));
        Announcement urgent = publish("紧急公告", AnnouncementLevel.URGENT, NOW.minusSeconds(20));
        entityManager.flush();

        List<Announcement> pending = findPending(7L, null);

        assertThat(pending).extracting(Announcement::getId).containsExactly(urgent.getId());
    }

    @Test
    @DisplayName("当日「发布过」计数含已撤回：撤回不回退额度，否则「发布→撤回→再发布」可绕过每日上限")
    void shouldCountRevokedAnnouncementInDailyPublishCount() {
        Announcement first = publish("公告一", AnnouncementLevel.NORMAL, NOW);
        publish("公告二", AnnouncementLevel.NORMAL, NOW.plusSeconds(60));

        first.revoke(NOW.plusSeconds(120));
        entityManager.flush();

        // 两条都在今天发布过。撤回改的是状态、不是发布时间，故计数仍为 2——
        // 若按「当前状态 = PUBLISHED」统计，这里会变成 1，上限即可被反复撤回绕过。
        assertThat(first.getStatus()).isEqualTo(AnnouncementStatus.REVOKED);
        assertThat(announcementRepository.countByPublishedAtGreaterThanEqual(NOW.minusSeconds(1)))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("从未发布过的草稿不计入当日发布数")
    void shouldNotCountDraftInDailyPublishCount() {
        publish("公告一", AnnouncementLevel.NORMAL, NOW);
        // 草稿：publishedAt 为空，SQL 的 NULL 比较天然把它排除
        announcementRepository.saveAndFlush(Announcement.draft(
                "草稿", "正文", AnnouncementLevel.NORMAL, NOW, null, 0, false, false, null, 1L));
        entityManager.flush();

        assertThat(announcementRepository.countByPublishedAtGreaterThanEqual(NOW.minusSeconds(1)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("登录用户的已读记录把该公告排除出待读")
    void shouldExcludeReadByUserId() {
        Announcement urgent = publish("紧急公告", AnnouncementLevel.URGENT, NOW.minusSeconds(10));
        announcementReadRepository.saveAndFlush(
                AnnouncementRead.of(urgent.getId(), 7L, null, NOW, 6_000L, true));
        entityManager.clear();

        assertThat(findPending(7L, null)).isEmpty();
    }

    @Test
    @DisplayName("游客设备维度的已读记录同样把公告排除出待读")
    void shouldExcludeReadByDeviceId() {
        Announcement urgent = publish("紧急公告", AnnouncementLevel.URGENT, NOW.minusSeconds(10));
        announcementReadRepository.saveAndFlush(
                AnnouncementRead.of(urgent.getId(), null, "dev-1", NOW, 6_000L, true));
        entityManager.clear();

        assertThat(findPending(null, "dev-1")).isEmpty();
    }

    @Test
    @DisplayName("尚未生效的公告不进待读")
    void shouldExcludeNotYetActive() {
        // startAt 在未来：此刻不生效
        publish("未来公告", AnnouncementLevel.URGENT, NOW.plusSeconds(3600));
        entityManager.flush();

        assertThat(findPending(7L, null)).isEmpty();
    }

    @Test
    @DisplayName("只查 URGENT（发帖 gate 口径）时返回紧急公告，普通公告被排除")
    void shouldReturnOnlyUrgentWhenLevelRestricted() {
        publish("普通公告", AnnouncementLevel.NORMAL, NOW.minusSeconds(10));
        Announcement urgent = publish("紧急公告", AnnouncementLevel.URGENT, NOW.minusSeconds(20));
        entityManager.flush();

        assertThat(findPending(7L, null, List.of(AnnouncementLevel.URGENT)))
                .extracting(Announcement::getId)
                .containsExactly(urgent.getId());
    }

    @Test
    @DisplayName("只查 URGENT 且无紧急公告时为空：未读普通公告不拦发帖（设计方案 5.4）")
    void shouldReturnEmptyForGateWhenOnlyNormalUnread() {
        publish("普通公告", AnnouncementLevel.NORMAL, NOW.minusSeconds(10));
        entityManager.flush();

        // 发帖 gate 的口径：没有紧急公告 → 空 → 放行发帖
        assertThat(findPending(7L, null, List.of(AnnouncementLevel.URGENT))).isEmpty();
        // 同一份数据走弹窗口径（不限级别）则能取到，证明过滤确实只在 gate 一侧生效
        assertThat(findPending(7L, null)).hasSize(1);
    }

    private List<Announcement> findPending(Long userId, String deviceId) {
        return findPending(userId, deviceId, List.of(AnnouncementLevel.values()));
    }

    private List<Announcement> findPending(
            Long userId, String deviceId, Collection<AnnouncementLevel> levels) {
        return announcementRepository.findPending(AnnouncementStatus.PUBLISHED,
                levels, AnnouncementLevel.URGENT, AnnouncementLevel.NORMAL, NOW, userId, deviceId,
                PageRequest.of(0, 1));
    }

    private Announcement publish(String title, AnnouncementLevel level, Instant startAt) {
        Announcement announcement = Announcement.draft(title, title + "正文", level, startAt, null,
                5, false, false, null, 99L);
        announcement.publish(NOW);
        return announcementRepository.saveAndFlush(announcement);
    }
}
