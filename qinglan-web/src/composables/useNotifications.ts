import { ref } from 'vue'

import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'
import type { NotificationItem } from '@/types/notification'

/**
 * 消息中心编排：列表分页 + 已读动作 + 未读计数。
 *
 * 请求失败一律由 http 拦截器弹窗（AGENTS.md 第七章），这里只结束 loading 态，
 * 页面依据列表是否为空渲染空态，不内联渲染错误文案。
 */
export function useNotifications() {
  const userStore = useUserStore()
  const notificationStore = useNotificationStore()

  const items = ref<NotificationItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  function applyPage(
    page: { items: NotificationItem[]; nextCursor: string | null },
    append: boolean,
  ): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  async function load(): Promise<void> {
    loading.value = true
    try {
      applyPage(await fetchNotifications(null), false)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loading.value = false
    }
  }

  async function loadMore(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value) {
      return
    }
    loadingMore.value = true
    try {
      applyPage(await fetchNotifications(cursor), true)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loadingMore.value = false
    }
  }

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
    const target = items.value.find((item) => item.id === id)
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
      items.value.forEach((item) => {
        item.readAt = item.readAt ?? now
      })
      notificationStore.setUnreadCount(0)
    } catch {
      // 错误已由拦截器提示
    }
  }

  return { items, loading, loadingMore, hasMore, load, loadMore, markRead, markAllRead, refreshUnread }
}
