import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import AnnouncementModal from './AnnouncementModal.vue'
import { markAnnouncementRead } from '@/api/announcement'
import { ApiError } from '@/api/http'
import { useAnnouncement } from '@/composables/useAnnouncement'
import type { PendingAnnouncement } from '@/types/announcement'

vi.mock('@/api/announcement', () => ({
  fetchPendingAnnouncement: vi.fn(),
  markAnnouncementRead: vi.fn(),
}))

const mockedMarkRead = vi.mocked(markAnnouncementRead)

function pending(overrides: Partial<PendingAnnouncement> = {}): PendingAnnouncement {
  return {
    id: 1,
    title: '紧急通知',
    content: '请所有同学注意……',
    level: 'URGENT',
    minReadSeconds: 5,
    requireScrollToEnd: false,
    requireConfirm: false,
    publishedAt: '2026-10-07T00:00:00Z',
    ...overrides,
  }
}

const { current, visible, remainingSeconds, scrolledToEnd, confirmed, openFromPrecondition } =
  useAnnouncement()
const { clearSkippedAnnouncements } = useAnnouncement()

let wrapper: VueWrapper | null = null

function mountModal(): VueWrapper {
  // Teleport 打到 body，测试里 stub 掉以便在组件树内断言
  return mount(AnnouncementModal, { global: { stubs: { Teleport: true } } })
}

describe('AnnouncementModal', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    current.value = null
    visible.value = false
    remainingSeconds.value = 0
    scrolledToEnd.value = false
    confirmed.value = false
    clearSkippedAnnouncements()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.useRealTimers()
  })

  it('无待读公告时不渲染任何内容', () => {
    wrapper = mountModal()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('标记已读失败时界面切出「重试 / 关闭」，用户能自己走出去', async () => {
    mockedMarkRead.mockRejectedValue(new ApiError('QL-500-001', '服务暂时不可用', 500))
    openFromPrecondition(pending({ minReadSeconds: 2 }))
    wrapper = mountModal()
    await nextTick()

    vi.advanceTimersByTime(2000)
    await nextTick()

    // 点一次「提交」——失败后不应把用户锁在弹窗里
    await wrapper.get('button').trigger('click')
    await nextTick()

    const labels = wrapper.findAll('button').map((button) => button.text())
    expect(labels).toEqual(['关闭', '重试'])

    const closeButton = wrapper.findAll('button').find((button) => button.text() === '关闭')
    expect((closeButton?.element as HTMLButtonElement).disabled).toBe(false)

    await closeButton?.trigger('click')
    await nextTick()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('倒计时未满时关闭按钮处于禁用态并显示倒计时文案', async () => {
    openFromPrecondition(pending({ minReadSeconds: 5 }))
    wrapper = mountModal()
    await nextTick()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    const close = wrapper.get('button')
    expect((close.element as HTMLButtonElement).disabled).toBe(true)
    expect(wrapper.text()).toContain('5s 后可关闭')
  })

  it('倒计时满且无附加要求时关闭按钮可用', async () => {
    openFromPrecondition(pending({ minReadSeconds: 2 }))
    wrapper = mountModal()
    await nextTick()

    vi.advanceTimersByTime(2000)
    await nextTick()

    const close = wrapper.get('button')
    expect((close.element as HTMLButtonElement).disabled).toBe(false)
    expect(wrapper.text()).toContain('我知道了')
  })
})
