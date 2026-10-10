import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useVisibilityPolling } from '@/composables/useVisibilityPolling'

const INTERVAL = 1_000

function setVisibility(state: 'visible' | 'hidden'): void {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
}

describe('useVisibilityPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setVisibility('visible')
  })

  afterEach(() => {
    vi.useRealTimers()
    setVisibility('visible')
  })

  it('默认不在 start 时立即执行，只按间隔触发', async () => {
    const run = vi.fn()
    const polling = useVisibilityPolling(run, { intervalMs: INTERVAL })

    polling.start()
    expect(run).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(INTERVAL)
    expect(run).toHaveBeenCalledTimes(1)

    polling.stop()
    await vi.advanceTimersByTimeAsync(INTERVAL * 2)
    expect(run).toHaveBeenCalledTimes(1)
  })

  it('immediate=true 时 start 立即执行一次，随后按间隔轮询', async () => {
    const run = vi.fn()
    const polling = useVisibilityPolling(run, { intervalMs: INTERVAL, immediate: true })

    polling.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(run).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(INTERVAL)
    expect(run).toHaveBeenCalledTimes(2)

    polling.stop()
  })

  it('切到后台暂停，切回可见恢复', async () => {
    const run = vi.fn()
    const polling = useVisibilityPolling(run, { intervalMs: INTERVAL })
    polling.start()

    setVisibility('hidden')
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(INTERVAL * 3)
    expect(run).not.toHaveBeenCalled()

    setVisibility('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(INTERVAL)
    expect(run).toHaveBeenCalledTimes(1)

    polling.stop()
  })

  it('上一轮未返回时不叠加请求（防重入）', async () => {
    let release: () => void = () => {}
    const run = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          release = resolve
        }),
    )
    const polling = useVisibilityPolling(run, { intervalMs: INTERVAL, immediate: true })
    polling.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(run).toHaveBeenCalledTimes(1)

    // 在途期间再过两个间隔，不叠加
    await vi.advanceTimersByTimeAsync(INTERVAL * 2)
    expect(run).toHaveBeenCalledTimes(1)

    release()
    await vi.advanceTimersByTimeAsync(INTERVAL)
    expect(run).toHaveBeenCalledTimes(2)

    polling.stop()
  })

  it('run 抛错被静默吞掉，不中断轮询', async () => {
    const run = vi.fn().mockRejectedValue(new Error('boom'))
    const polling = useVisibilityPolling(run, { intervalMs: INTERVAL, immediate: true })
    polling.start()
    await vi.advanceTimersByTimeAsync(0)

    // 不抛到测试外即为通过；下一轮仍会执行
    await vi.advanceTimersByTimeAsync(INTERVAL)
    expect(run).toHaveBeenCalledTimes(2)

    polling.stop()
  })
})
