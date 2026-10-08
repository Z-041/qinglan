import { ref } from 'vue'

import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@shared/stores/user'
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
  /** 首屏是否失败过。用于把「加载失败」与「还没有消息」分成两屏 */
  const failed = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /**
   * 请求序号。标记已读后刷新与用户重新进入可能同时在飞，**先发的不一定先到**：
   * 没有它的话，晚到的旧响应会把新结果覆盖掉。
   */
  let requestSeq = 0

  function applyPage(
    page: { items: NotificationItem[]; nextCursor: string | null },
    append: boolean,
  ): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  async function load(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    // 先清干净再请求：否则本次失败时列表与游标会留在上一次的结果集上，
    // 此后点「加载更多」就会拿「新一次 + 旧游标」去请求
    items.value = []
    cursor = null
    hasMore.value = false
    try {
      const page = await fetchNotifications(null)
      // 过期响应必须丢弃：refresh 与用户再次进入可能同时在飞，先发的不一定先到
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, false)
    } catch {
      // 错误已由拦截器提示；只有当前这次请求才有权把状态置为失败
      if (seq === requestSeq) {
        failed.value = true
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  async function loadMore(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value) {
      return
    }
    // 记下发起时的序号：期间若刷新过（requestSeq 会 +1），这次的响应必须丢弃
    const seq = requestSeq
    loadingMore.value = true
    try {
      const page = await fetchNotifications(cursor)
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, true)
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

  return {
    items,
    loading,
    loadingMore,
    hasMore,
    failed,
    load,
    loadMore,
    markRead,
    markAllRead,
    refreshUnread,
  }
}
