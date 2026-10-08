import { httpGet, httpPost } from '@shared/api/http'

import type { CursorPage } from '@shared/types/api'
import type {
  AnnouncementSummary,
  MarkAnnouncementReadPayload,
  PendingAnnouncement,
} from '@/types/announcement'

/** 拉取当前下一条未读公告；无未读时后端返回 null。游客需带上 deviceId（计划 D4）。 */
export function fetchPendingAnnouncement(
  deviceId: string | null,
): Promise<PendingAnnouncement | null> {
  return httpGet<PendingAnnouncement | null>('/announcements/pending', { deviceId })
}

/** 标记公告已读；真实停留时长与滚动/勾选条件由服务端校验（不变量 #6） */
export function markAnnouncementRead(
  id: number,
  payload: MarkAnnouncementReadPayload,
): Promise<void> {
  return httpPost<void>(`/announcements/${id}/read`, payload)
}

/** 公告历史列表，游标分页；deviceId 用于标注游客已读 */
export function fetchAnnouncementHistory(
  cursor: string | null,
  deviceId: string | null,
): Promise<CursorPage<AnnouncementSummary>> {
  return httpGet<CursorPage<AnnouncementSummary>>('/announcements', { cursor, deviceId })
}
