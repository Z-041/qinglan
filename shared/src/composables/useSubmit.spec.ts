import { describe, expect, it, vi } from 'vitest'

import { useSubmit } from '@shared/composables/useSubmit'

describe('useSubmit', () => {
  it('成功：返回 true，执行后复位 submitting', async () => {
    const submit = useSubmit()
    const action = vi.fn().mockResolvedValue(undefined)

    await expect(submit.run(action)).resolves.toBe(true)
    expect(action).toHaveBeenCalledTimes(1)
    expect(submit.submitting.value).toBe(false)
  })

  it('在途期间 submitting 为真', async () => {
    const submit = useSubmit()
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const action = vi.fn().mockReturnValue(gate)

    const done = submit.run(action)
    await Promise.resolve()
    expect(submit.submitting.value).toBe(true)

    release()
    await done
    expect(submit.submitting.value).toBe(false)
  })

  it('失败：返回 false，不冒泡异常，且复位 submitting', async () => {
    const submit = useSubmit()
    const action = vi.fn().mockRejectedValue(new Error('boom'))

    await expect(submit.run(action)).resolves.toBe(false)
    expect(submit.submitting.value).toBe(false)
  })

  it('两次提交各跑一次 action，互不干扰', async () => {
    const submit = useSubmit()
    const first = vi.fn().mockResolvedValue(undefined)
    const second = vi.fn().mockResolvedValue(undefined)

    await submit.run(first)
    await submit.run(second)

    expect(first).toHaveBeenCalledTimes(1)
    expect(second).toHaveBeenCalledTimes(1)
  })
})
