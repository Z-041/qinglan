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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公告持久层测试：真实 MySQL（本机 qinglan_test），不 mock 数据库行为。
 *
 * <p>重点验证 {@code findPending} 的显式优先级排序与「按 userId / deviceId 任一命中即已读」的排除逻辑——
 * 这段 JPQL 的 enum 参数 CASE 排序与可空参数排除只有真跑才能确认。</p>
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

    private List<Announcement> findPending(Long userId, String deviceId) {
        return announcementRepository.findPending(AnnouncementStatus.PUBLISHED,
                AnnouncementLevel.URGENT, AnnouncementLevel.NORMAL, NOW, userId, deviceId,
                PageRequest.of(0, 1));
    }

    private Announcement publish(String title, AnnouncementLevel level, Instant startAt) {
        Announcement announcement = Announcement.draft(title, title + "正文", level, startAt, null,
                5, false, false, null, 99L);
        announcement.publish(NOW);
        return announcementRepository.saveAndFlush(announcement);
    }
}
