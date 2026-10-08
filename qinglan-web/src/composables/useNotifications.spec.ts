import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAuthToken } from '@shared/api/http'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import { useNotifications } from '@/composables/useNotifications'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@shared/stores/user'
import type { NotificationItem } from '@/types/notification'

// 工厂必须覆盖 useNotifications 从该模块导入的全部符号
vi.mock('@/api/notification', () => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const mockedFetchNotifications = vi.mocked(fetchNotifications)
const mockedFetchUnreadCount = vi.mocked(fetchUnreadCount)
const mockedMarkRead = vi.mocked(markNotificationRead)
const mockedMarkAll = vi.mocked(markAllNotificationsRead)

function notification(id: number, overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id,
    type: 'COMMENT',
    title: `通知 ${id}`,
    body: null,
    link: null,
    readAt: null,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

describe('useNotifications', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    // 令牌现在以 api/http 的内存态为准（跨刷新持久化只作兜底），该内存态在同一个测试文件内
    // 不会自动复位：不清掉的话，前面「已登录」用例设过的令牌会漏进「未登录」用例
    setAuthToken(null)
    vi.clearAllMocks()
  })

  it('load 拉第一页，loadMore 带末条游标并追加', async () => {
    mockedFetchNotifications
      .mockResolvedValueOnce({ items: [notification(2)], nextCursor: 'c1' })
      .mockResolvedValueOnce({ items: [notification(1)], nextCursor: null })

    const notifications = useNotifications()
    await notifications.load()
    await notifications.loadMore()

    expect(mockedFetchNotifications).toHaveBeenNthCalledWith(1, null)
    expect(mockedFetchNotifications).toHaveBeenNthCalledWith(2, 'c1')
    expect(notifications.items.value.map((item) => item.id)).toEqual([2, 1])
    expect(notifications.hasMore.value).toBe(false)
    expect(notifications.loading.value).toBe(false)
  })

  it('末页后不再请求下一页', async () => {
    mockedFetchNotifications.mockResolvedValue({ items: [notification(1)], nextCursor: null })

    const notifications = useNotifications()
    await notifications.load()
    await notifications.loadMore()

    expect(mockedFetchNotifications).toHaveBeenCalledTimes(1)
  })

  it('markAllRead 后未读计数归零，本地条目标为已读', async () => {
    mockedFetchNotifications.mockResolvedValue({ items: [notification(1)], nextCursor: null })
    mockedMarkAll.mockResolvedValue(undefined)

    const notifications = useNotifications()
    const store = useNotificationStore()
    store.setUnreadCount(5)

    await notifications.load()
    await notifications.markAllRead()

    expect(mockedMarkAll).toHaveBeenCalledTimes(1)
    expect(store.unreadCount).toBe(0)
    expect(notifications.items.value[0]?.readAt).not.toBeNull()
  })

  it('markRead 成功后刷新未读计数', async () => {
    useUserStore().setAccessToken('access-token')
    mockedFetchNotifications.mockResolvedValue({ items: [notification(1)], nextCursor: null })
    mockedMarkRead.mockResolvedValue(undefined)
    mockedFetchUnreadCount.mockResolvedValue({ count: 4 })

    const notifications = useNotifications()
    await notifications.load()
    await notifications.markRead(1)

    expect(mockedMarkRead).toHaveBeenCalledWith(1)
    expect(useNotificationStore().unreadCount).toBe(4)
  })

  it('未登录时 refreshUnread 不发请求', async () => {
    const notifications = useNotifications()
    await notifications.refreshUnread()

    expect(mockedFetchUnreadCount).not.toHaveBeenCalled()
  })

  it('已登录时 refreshUnread 把计数写入 store', async () => {
    useUserStore().setAccessToken('access-token')
    mockedFetchUnreadCount.mockResolvedValue({ count: 7 })

    const notifications = useNotifications()
    await notifications.refreshUnread()

    expect(useNotificationStore().unreadCount).toBe(7)
  })
})
