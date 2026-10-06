import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchPendingAnnouncement, markAnnouncementRead } from '@/api/announcement'
import { DEVICE_ID_STORAGE_KEY, useAnnouncement } from '@/composables/useAnnouncement'
import type { PendingAnnouncement } from '@/types/announcement'

vi.mock('@/api/announcement', () => ({
  fetchPendingAnnouncement: vi.fn(),
  markAnnouncementRead: vi.fn(),
}))

const mockedFetchPending = vi.mocked(fetchPendingAnnouncement)
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

const {
  current,
  visible,
  remainingSeconds,
  scrolledToEnd,
  confirmed,
  submitting,
  canClose,
  loadNext,
  start,
  closeAndContinue,
  noteScrolledToEnd,
} = useAnnouncement()

describe('useAnnouncement', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    localStorage.clear()
    // 模块级单例：每个用例前复位，避免上一条公告的状态残留
    current.value = null
    visible.value = false
    remainingSeconds.value = 0
    scrolledToEnd.value = false
    confirmed.value = false
    submitting.value = false
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('弹窗刚打开、倒计时未满时不得标记已读（不变量 #6）', async () => {
    mockedFetchPending.mockResolvedValue(pending({ minReadSeconds: 5 }))

    await loadNext()
    expect(current.value?.id).toBe(1)
    expect(canClose.value).toBe(false)

    // 用户想立刻关掉——时长没到，不应发 read 请求，弹窗也不关闭
    await closeAndContinue()

    expect(mockedMarkRead).not.toHaveBeenCalled()
    expect(current.value?.id).toBe(1)
  })

  it('时长满但需滚动到底且尚未到底时不能关闭，也不标记已读', async () => {
    mockedFetchPending.mockResolvedValue(pending({ requireScrollToEnd: true }))

    await loadNext()
    vi.advanceTimersByTime(5000)

    expect(remainingSeconds.value).toBe(0)
    expect(canClose.value).toBe(false)

    await closeAndContinue()
    expect(mockedMarkRead).not.toHaveBeenCalled()
  })

  it('时长满但需勾选且尚未勾选时不能关闭，也不标记已读', async () => {
    mockedFetchPending.mockResolvedValue(pending({ requireConfirm: true }))

    await loadNext()
    vi.advanceTimersByTime(5000)

    expect(canClose.value).toBe(false)

    await closeAndContinue()
    expect(mockedMarkRead).not.toHaveBeenCalled()
  })

  it('时长满且滚动/勾选齐备时才标记已读，并如实上报真实停留时长', async () => {
    mockedFetchPending.mockResolvedValue(
      pending({ requireScrollToEnd: true, requireConfirm: true }),
    )
    mockedMarkRead.mockResolvedValue(undefined)

    await loadNext()
    vi.advanceTimersByTime(5000)
    noteScrolledToEnd()
    confirmed.value = true

    expect(canClose.value).toBe(true)

    await closeAndContinue()

    expect(mockedMarkRead).toHaveBeenCalledTimes(1)
    const [id, payload] = mockedMarkRead.mock.calls[0]
    expect(id).toBe(1)
    expect(payload.durationMs).toBeGreaterThanOrEqual(5000)
    expect(payload.scrolledToEnd).toBe(true)
    expect(payload.confirmed).toBe(true)
  })

  it('普通公告倒计时满即可关闭，无需滚动', async () => {
    mockedFetchPending.mockResolvedValue(pending())
    mockedMarkRead.mockResolvedValue(undefined)

    await loadNext()
    expect(canClose.value).toBe(false)

    vi.advanceTimersByTime(5000)

    expect(canClose.value).toBe(true)
    expect(mockedMarkRead).not.toHaveBeenCalled()
  })

  it('拉不到待读公告时不展示弹窗', async () => {
    mockedFetchPending.mockResolvedValue(null)

    await start()

    expect(visible.value).toBe(false)
    expect(current.value).toBeNull()
  })

  it('读完一条后继续拉取下一条（绝不堆叠）', async () => {
    mockedFetchPending
      .mockResolvedValueOnce(pending({ id: 1 }))
      .mockResolvedValueOnce(pending({ id: 2 }))
    mockedMarkRead.mockResolvedValue(undefined)

    await loadNext()
    expect(current.value?.id).toBe(1)

    vi.advanceTimersByTime(5000)
    await closeAndContinue()

    expect(mockedMarkRead).toHaveBeenCalledTimes(1)
    expect(mockedFetchPending).toHaveBeenCalledTimes(2)
    expect(current.value?.id).toBe(2)
  })

  it('游客也会带上并持久化 deviceId（计划 D4）', async () => {
    mockedFetchPending.mockResolvedValue(null)

    await loadNext()

    const passed = mockedFetchPending.mock.calls[0][0]
    expect(passed).toBeTruthy()
    expect(localStorage.getItem(DEVICE_ID_STORAGE_KEY)).toBe(passed)
  })
})
