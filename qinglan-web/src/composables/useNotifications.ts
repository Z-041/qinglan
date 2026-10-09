import { useCursorList } from '@shared/composables/useCursorList'
import { useUserStore } from '@shared/stores/user'

import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationItem } from '@/types/notification'

/**
 * 消息中心编排：列表分页（状态机收在 {@link useCursorList}）+ 已读动作 + 未读计数。
 *
 * 请求失败一律由 http 拦截器弹窗（AGENTS.md 第七章），这里只结束 loading 态，
 * 页面依据列表是否为空渲染空态，不内联渲染错误文案。
 */
export function useNotifications() {
  const userStore = useUserStore()
  const notificationStore = useNotificationStore()

  const list = useCursorList<NotificationItem>({
    fetchPage: (cursor) => fetchNotifications(cursor),
  })

  /** 刷新未读计数；未登录时直接返回，不发请求（红点只在已认证时展示） */
  async function refreshUnread(): Promise<void> {
    if (!userStore.isAuthenticated) {
      return
    }
    try {
      const { count } = await fetchUnreadCount()
      notificationStore.setUnreadCount(count)
    } catch {
      // 错误已由拦截器提示
    }
  }

  /** 单条已读；本已读的条目不重复请求，成功后刷新计数 */
  async function markRead(id: number): Promise<void> {
    const target = list.items.value.find((item) => item.id === id)
    if (target?.readAt) {
      return
    }
    try {
      await markNotificationRead(id)
      if (target) {
        target.readAt = new Date().toISOString()
      }
      await refreshUnread()
    } catch {
      // 错误已由拦截器提示
    }
  }

  /** 全部已读；成功后本地条目标已读并清零计数 */
  async function markAllRead(): Promise<void> {
    try {
      await markAllNotificationsRead()
      const now = new Date().toISOString()
      list.items.value.forEach((item) => {
        item.readAt = item.readAt ?? now
      })
      notificationStore.setUnreadCount(0)
    } catch {
      // 错误已由拦截器提示
    }
  }

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    load: list.refresh,
    loadMore: list.loadMore,
    markRead,
    markAllRead,
    refreshUnread,
  }
}
