import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  QUEUE_POLL_INTERVAL_MS,
  isPrefixEqual,
  useQueuePolling,
  type QueuePollingOptions,
} from '@/composables/useQueuePolling'

function setVisibility(state: 'visible' | 'hidden'): void {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
}

describe('isPrefixEqual', () => {
  it('首屏与展示列表前缀一致视为无变化', () => {
    expect(isPrefixEqual(['a', 'b'], ['a', 'b'])).toBe(true)
    expect(isPrefixEqual(['a', 'b'], ['a', 'b', 'c'])).toBe(true)
    // 空队列无可比对，不当作有新内容
    expect(isPrefixEqual([], ['a'])).toBe(true)
    expect(isPrefixEqual([], [])).toBe(true)
  })

  it('长度不足或元素不同都视为有变化', () => {
    expect(isPrefixEqual(['a'], [])).toBe(false)
    expect(isPrefixEqual(['a', 'b'], ['a'])).toBe(false)
    expect(isPrefixEqual(['b'], ['a'])).toBe(false)
  })
})

describe('useQueuePolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setVisibility('visible')
  })

  afterEach(() => {
    vi.useRealTimers()
    setVisibility('visible')
  })

  function setup(overrides: Partial<QueuePollingOptions> = {}) {
    const fetchHeadKeys = vi.fn(async (): Promise<string[]> => ['a'])
    const onRefresh = vi.fn(async (): Promise<void> => {})
    const displayed = { keys: ['a'] }
    const polling = useQueuePolling({
      fetchHeadKeys,
      getDisplayedKeys: () => displayed.keys,
      isBusy: () => false,
      onRefresh,
      ...overrides,
    })
    return { polling, fetchHeadKeys, onRefresh, displayed }
  }

  it('按间隔轮询：只有到达间隔才触发一次探针', async () => {
    const { polling, fetchHeadKeys } = setup()
    polling.start()
    expect(fetchHeadKeys).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)
    expect(fetchHeadKeys).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)
    expect(fetchHeadKeys).toHaveBeenCalledTimes(2)

    polling.stop()
    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS * 2)
    expect(fetchHeadKeys).toHaveBeenCalledTimes(2)
  })

  it('切到后台即暂停轮询，切回可见后恢复', async () => {
    const { polling, fetchHeadKeys } = setup()
    polling.start()

    setVisibility('hidden')
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS * 3)
    expect(fetchHeadKeys).not.toHaveBeenCalled()

    setVisibility('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)
    expect(fetchHeadKeys).toHaveBeenCalledTimes(1)

    polling.stop()
  })

  it('列表无变化时不打扰：不刷新，也不挂角标', async () => {
    const { polling, onRefresh } = setup()
    polling.start()

    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)

    expect(onRefresh).not.toHaveBeenCalled()
    expect(polling.hasNewContent.value).toBe(false)
    polling.stop()
  })

  it('处置进行中（弹窗打开 / 提交中）时不替换列表，只把角标挂起', async () => {
    const { polling, fetchHeadKeys, onRefresh } = setup({ isBusy: () => true })
    fetchHeadKeys.mockResolvedValue(['new', 'a'])
    polling.start()

    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)

    expect(polling.hasNewContent.value).toBe(true)
    expect(onRefresh).not.toHaveBeenCalled()
    polling.stop()
  })

  it('空闲时发现新内容即自动合并刷新，刷新到位后清除角标', async () => {
    const { polling, fetchHeadKeys, onRefresh, displayed } = setup()
    fetchHeadKeys.mockResolvedValue(['new', 'a'])
    onRefresh.mockImplementation(async () => {
      displayed.keys = ['new', 'a']
    })
    polling.start()

    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)

    expect(onRefresh).toHaveBeenCalledTimes(1)
    expect(polling.hasNewContent.value).toBe(false)
    polling.stop()
  })

  it('刷新失败导致列表仍未更新时保留角标，下一轮继续提示', async () => {
    const { polling, fetchHeadKeys, onRefresh } = setup()
    fetchHeadKeys.mockResolvedValue(['new', 'a'])
    onRefresh.mockResolvedValue(undefined)
    polling.start()

    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)

    expect(polling.hasNewContent.value).toBe(true)
    polling.stop()
  })

  it('点击角标合并刷新并清除标记', async () => {
    const { polling, fetchHeadKeys, onRefresh, displayed } = setup({ isBusy: () => true })
    fetchHeadKeys.mockResolvedValue(['new'])
    polling.start()
    await vi.advanceTimersByTimeAsync(QUEUE_POLL_INTERVAL_MS)
    expect(polling.hasNewContent.value).toBe(true)

    onRefresh.mockImplementation(async () => {
      displayed.keys = ['new']
    })
    await polling.applyNow()

    expect(onRefresh).toHaveBeenCalledTimes(1)
    expect(polling.hasNewContent.value).toBe(false)
    polling.stop()
  })
})
