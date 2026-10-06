import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchNotifications, fetchUnreadCount } from '@/api/notification'
import { useUserStore } from '@/stores/user'
import NotificationsView from '@/views/NotificationsView.vue'
import type { NotificationItem } from '@/types/notification'

vi.mock('@/api/notification', () => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const mockedFetchNotifications = vi.mocked(fetchNotifications)
const mockedFetchUnreadCount = vi.mocked(fetchUnreadCount)

const RouterLinkStub = { props: ['to'], template: '<a :href="to"><slot /></a>' }

function notification(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id: 1,
    type: 'LIKE',
    title: '有人赞了你的帖子',
    body: null,
    link: '/posts/12',
    readAt: null,
    createdAt: '2026-10-07T00:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(NotificationsView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('NotificationsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // 未登录时 refreshUnread 会提前返回不请求，故必须先造出登录态
    useUserStore().setAccessToken('test-token')
    vi.clearAllMocks()
    mockedFetchUnreadCount.mockResolvedValue({ count: 0 })
  })

  it('没有消息时渲染空态', async () => {
    mockedFetchNotifications.mockResolvedValue({ items: [], nextCursor: null })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('还没有消息')
  })

  it('无未读时「全部已读」按钮禁用，有未读时可用', async () => {
    mockedFetchNotifications.mockResolvedValue({ items: [], nextCursor: null })

    mockedFetchUnreadCount.mockResolvedValue({ count: 0 })
    const zero = mountView()
    await flushPromises()
    const zeroButton = zero.findAll('button').find((button) => button.text() === '全部已读')
    expect(zeroButton?.attributes('disabled')).toBeDefined()

    mockedFetchUnreadCount.mockResolvedValue({ count: 2 })
    const two = mountView()
    await flushPromises()
    const twoButton = two.findAll('button').find((button) => button.text() === '全部已读')
    expect(twoButton?.attributes('disabled')).toBeUndefined()
  })

  it('带 link 的条目渲染成可跳转的链接，标题与正文如实展示', async () => {
    mockedFetchNotifications.mockResolvedValue({
      items: [notification({ body: '内容是「今晚停水」' })],
      nextCursor: null,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('a[href="/posts/12"]').text()).toContain('有人赞了你的帖子')
    expect(wrapper.text()).toContain('内容是「今晚停水」')
  })
})
