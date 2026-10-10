import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { dismissToast, pushToast, useToast } from './useToast'

describe('useToast', () => {
  const { toasts } = useToast()

  beforeEach(() => {
    vi.useFakeTimers()
    toasts.splice(0)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('推送后提示进入列表，默认类型为 info', () => {
    pushToast('已保存')

    expect(toasts).toHaveLength(1)
    expect(toasts[0]).toMatchObject({ message: '已保存', type: 'info' })
  })

  it('到时后自动消失，不会一直留在页面上', () => {
    pushToast('验证码不正确', 'error')
    expect(toasts).toHaveLength(1)

    vi.advanceTimersByTime(4000)

    expect(toasts).toHaveLength(0)
  })

  it('提示数超上限时挤掉最旧的，始终只保留最新的 MAX_TOASTS 条', () => {
    for (let i = 1; i <= 7; i += 1) {
      pushToast(`第 ${i} 条`)
    }

    expect(toasts).toHaveLength(5)
    expect(toasts.map((toast) => toast.message)).toEqual([
      '第 3 条',
      '第 4 条',
      '第 5 条',
      '第 6 条',
      '第 7 条',
    ])
  })

  it('手动关闭立即移除，且旧提示的定时器不会误删后续提示', () => {
    pushToast('第一条')
    dismissToast(toasts[0].id)
    expect(toasts).toHaveLength(0)

    pushToast('第二条')
    vi.advanceTimersByTime(2000)
    expect(toasts.map((toast) => toast.message)).toEqual(['第二条'])

    vi.advanceTimersByTime(2000)
    expect(toasts).toHaveLength(0)
  })
})
