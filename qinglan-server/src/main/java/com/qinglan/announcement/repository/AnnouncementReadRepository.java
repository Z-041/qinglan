package com.qinglan.announcement.repository;

import com.qinglan.announcement.entity.AnnouncementRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, Long> {

    /** 标记已读时定位登录用户的既有记录（登录用户只填 user_id） */
    Optional<AnnouncementRead> findByAnnouncementIdAndUserId(Long announcementId, Long userId);

    /** 标记已读时定位游客的既有记录（游客只填 device_id） */
    Optional<AnnouncementRead> findByAnnouncementIdAndDeviceId(Long announcementId, String deviceId);

    /** 历史列表批量标「我是否已读」，避免逐条查询造成 N+1 */
    List<AnnouncementRead> findByAnnouncementIdInAndUserId(Collection<Long> announcementIds, Long userId);

    /**
     * 同上，按设备维度。仅供游客历史列表使用：登录用户只查账号维度（见
     * AnnouncementService#loadReadAnnouncementIds），游客期已读不会合并到账号，
     * 以避免共用设备上他人的游客记录污染本人已读状态（设计 5.6 的登录合并尚未落地）。
     */
    List<AnnouncementRead> findByAnnouncementIdInAndDeviceId(Collection<Long> announcementIds, String deviceId);
}
