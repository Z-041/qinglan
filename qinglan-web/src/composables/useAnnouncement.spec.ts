import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchPendingAnnouncement, markAnnouncementRead } from '@/api/announcement'
import { ApiError } from '@/api/http'
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
  confirmed,
  canClose,
  readFailed,
  loadNext,
  refreshPending,
  openFromPrecondition,
  closeAndContinue,
  forceClose,
  reset,
  clearSkippedAnnouncements,
  noteScrolledToEnd,
  deviceId,
} = useAnnouncement()

describe('useAnnouncement', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    localStorage.clear()
    // 模块级单例：每个用例前复位，避免上一条公告的状态残留
    reset()
    // 「本次会话已放行」记录也是模块级的，必须一并清掉否则会串用例
    clearSkippedAnnouncements()
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

    await refreshPending()

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

  it('存储不可用（禁用站点数据）时 deviceId 不抛错且在会话内稳定', () => {
    const getItem = vi
      .spyOn(Storage.prototype, 'getItem')
      .mockImplementation(() => {
        throw new Error('SecurityError')
      })
    const setItem = vi
      .spyOn(Storage.prototype, 'setItem')
      .mockImplementation(() => {
        throw new Error('SecurityError')
      })

    try {
      // 旧实现裸调 localStorage：这里会直接把异常抛到调用方（应用启动路径上就是白屏）
      expect(() => deviceId()).not.toThrow()
      // 且不能每次换一个 id——否则同一条公告会被反复当成「没读过」、弹个不停
      expect(deviceId()).toBe(deviceId())
    } finally {
      getItem.mockRestore()
      setItem.mockRestore()
    }
  })

  // ---------------------------------------------------------- 失败时不能把用户锁死

  it('标记已读返回 404（公告被撤回）时放行关闭，不再把人锁在弹窗里', async () => {
    mockedFetchPending.mockResolvedValueOnce(pending({ id: 1 })).mockResolvedValueOnce(null)
    mockedMarkRead.mockRejectedValue(new ApiError('QL-404-007', '公告不存在', 404))

    await loadNext()
    vi.advanceTimersByTime(5000)
    await closeAndContinue()

    // 关键：404 重试永远不会成功，必须直接放行，否则用户永久卡在弹窗
    expect(current.value).toBeNull()
    expect(mockedFetchPending).toHaveBeenCalledTimes(2)
  })

  it('网络类失败可重试，连续失败到上限后仍然能关掉', async () => {
    mockedFetchPending.mockResolvedValue(pending({ id: 1 }))
    mockedMarkRead.mockRejectedValue(new ApiError('QL-500-001', '服务暂时不可用', 500))

    await loadNext()
    vi.advanceTimersByTime(5000)

    // 第一次失败：留在弹窗，界面切出「重试 / 关闭」
    await closeAndContinue()
    expect(current.value?.id).toBe(1)
    expect(readFailed.value).toBe(true)

    // 用户显式关闭是允许的——真强制在 428，放行不会让用户获得任何额外能力
    await forceClose()
    expect(current.value).toBeNull()
  })

  it('未失败过时 forceClose 不放行（正常强制阅读流程不给这个出口）', async () => {
    mockedFetchPending.mockResolvedValue(pending({ id: 1 }))

    await loadNext()
    await forceClose()

    expect(current.value?.id).toBe(1)
  })

  it('连续失败达到上限后自动放行，不必等用户点关闭', async () => {
    mockedFetchPending.mockResolvedValueOnce(pending({ id: 1 })).mockResolvedValueOnce(null)
    mockedMarkRead.mockRejectedValue(new ApiError('QL-500-001', '服务暂时不可用', 500))

    await loadNext()
    vi.advanceTimersByTime(5000)

    await closeAndContinue()
    await closeAndContinue()
    await closeAndContinue()

    expect(current.value).toBeNull()
  })

  // ---------------------------------------------------------- 不覆盖正在读的那条

  it('已有弹窗时 openFromPrecondition 不覆盖，也不重置倒计时', async () => {
    mockedFetchPending.mockResolvedValue(pending({ id: 1, minReadSeconds: 5 }))

    await loadNext()
    vi.advanceTimersByTime(2000)
    expect(remainingSeconds.value).toBe(3)

    // 428 拿回同一条公告：不能把已读时长清零
    openFromPrecondition(pending({ id: 1, minReadSeconds: 5 }))

    expect(remainingSeconds.value).toBe(3)
  })

  it('已有弹窗时 refreshPending 不重复请求（切页面不该打断阅读）', async () => {
    mockedFetchPending.mockResolvedValue(pending({ id: 1 }))

    await loadNext()
    await refreshPending()

    expect(mockedFetchPending).toHaveBeenCalledTimes(1)
  })

  it('拉取失败会重试一次', async () => {
    mockedFetchPending
      .mockRejectedValueOnce(new Error('网络异常'))
      .mockResolvedValueOnce(pending({ id: 3 }))

    const loading = refreshPending()
    await vi.advanceTimersByTimeAsync(2000)
    await loading

    expect(mockedFetchPending).toHaveBeenCalledTimes(2)
    expect(current.value?.id).toBe(3)
  })

  it('倒计时按目标时刻计算，定时器被节流漏跑也不会多等', async () => {
    mockedFetchPending.mockResolvedValue(pending({ minReadSeconds: 3 }))

    await loadNext()
    expect(remainingSeconds.value).toBe(3)

    // 模拟后台标签页：时间前进了，但 tick 没跑；补一次 tick 后应按真实时刻结算
    vi.setSystemTime(Date.now() + 3000)
    vi.advanceTimersByTime(1000)

    expect(remainingSeconds.value).toBe(0)
    expect(canClose.value).toBe(true)
  })
})
