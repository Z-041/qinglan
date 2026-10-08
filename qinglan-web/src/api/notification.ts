import { httpGet, httpPost } from '@shared/api/http'

import type { CursorPage } from '@shared/types/api'
import type { NotificationItem, UnreadCount } from '@/types/notification'

/** 通知列表：游标分页，cursor 为空表示第一页 */
export function fetchNotifications(cursor: string | null = null): Promise<CursorPage<NotificationItem>> {
  return httpGet<CursorPage<NotificationItem>>('/notifications', { cursor })
}

/** 未读计数，用于顶部红点 */
export function fetchUnreadCount(): Promise<UnreadCount> {
  return httpGet<UnreadCount>('/notifications/unread-count')
}

/** 单条标为已读 */
export function markNotificationRead(id: number): Promise<void> {
  return httpPost<void>(`/notifications/${id}/read`)
}

/** 全部标为已读 */
export function markAllNotificationsRead(): Promise<void> {
  return httpPost<void>('/notifications/read-all')
}
