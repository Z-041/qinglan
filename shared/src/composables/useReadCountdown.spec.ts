import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useReadCountdown } from './useReadCountdown'

describe('useReadCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('倒计时按秒递减，走完前不可关闭，走完后可关闭', () => {
    const gate = useReadCountdown({ requireScrollToEnd: () => false, requireConfirm: () => false })

    gate.start(3)
    expect(gate.remainingSeconds.value).toBe(3)
    expect(gate.canClose.value).toBe(false)

    vi.advanceTimersByTime(3000)
    expect(gate.remainingSeconds.value).toBe(0)
    expect(gate.canClose.value).toBe(true)
  })

  it('需滚动到底时，未滚到底不可关闭；标记后可关闭', () => {
    const gate = useReadCountdown({ requireScrollToEnd: () => true, requireConfirm: () => false })
    gate.start(0)

    expect(gate.canClose.value).toBe(false)
    gate.noteScrolledToEnd()
    expect(gate.canClose.value).toBe(true)
  })

  it('需勾选时，未勾选不可关闭；勾选后可关闭', () => {
    const gate = useReadCountdown({ requireScrollToEnd: () => false, requireConfirm: () => true })
    gate.start(0)

    expect(gate.canClose.value).toBe(false)
    gate.setConfirmed(true)
    expect(gate.canClose.value).toBe(true)
  })

  it('start 会重置上一轮的滚动与勾选信号并重新计时', () => {
    const gate = useReadCountdown({ requireScrollToEnd: () => true, requireConfirm: () => true })
    gate.start(0)
    gate.noteScrolledToEnd()
    gate.setConfirmed(true)
    expect(gate.canClose.value).toBe(true)

    gate.start(2)
    expect(gate.scrolledToEnd.value).toBe(false)
    expect(gate.confirmed.value).toBe(false)
    expect(gate.canClose.value).toBe(false)
  })

  it('reset 归零信号并停表', () => {
    const gate = useReadCountdown({ requireScrollToEnd: () => false, requireConfirm: () => false })
    gate.start(5)
    gate.reset()

    expect(gate.remainingSeconds.value).toBe(0)
    vi.advanceTimersByTime(10000)
    expect(gate.remainingSeconds.value).toBe(0)
  })

  it('标签页挂起造成的时间跳跃按 deadline 一次性校正，不累减', () => {
    const gate = useReadCountdown({ requireScrollToEnd: () => false, requireConfirm: () => false })
    gate.start(10)

    // 挂起 10 秒期间 interval 被节流几乎不触发；恢复后剩余应直接为 0
    vi.advanceTimersByTime(10000)
    expect(gate.canClose.value).toBe(true)
  })
})
