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
