import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useDispositionFinisher } from '@/composables/useDispositionFinisher'
import { pushToast } from '@shared/composables/useToast'

vi.mock('@shared/composables/useToast', () => ({ pushToast: vi.fn() }))

const mockedToast = vi.mocked(pushToast)

describe('useDispositionFinisher', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('按 清理 → 提示 → 刷新 → 焦点推进 的顺序执行', async () => {
    const order: string[] = []
    const finisher = useDispositionFinisher({
      cleanup: () => order.push('cleanup'),
      refresh: () => {
        order.push('refresh')
        return Promise.resolve()
      },
      advance: () => order.push('advance'),
    })
    mockedToast.mockImplementation(() => {
      order.push('toast')
    })

    await finisher.finish('已通过')

    expect(order).toEqual(['cleanup', 'toast', 'refresh', 'advance'])
    expect(mockedToast).toHaveBeenCalledWith('已通过', 'info')
  })

  it('未提供 cleanup / advance 时只做提示与刷新', async () => {
    const refresh = vi.fn().mockResolvedValue(undefined)
    const finisher = useDispositionFinisher({ refresh })

    await finisher.finish('已驳回')

    expect(mockedToast).toHaveBeenCalledWith('已驳回', 'info')
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('等 refresh 完成后才推进焦点', async () => {
    const calls: string[] = []
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const finisher = useDispositionFinisher({
      refresh: async () => {
        calls.push('refresh:start')
        await gate
        calls.push('refresh:end')
      },
      advance: () => calls.push('advance'),
    })

    const done = finisher.finish('done')
    await Promise.resolve()
    expect(calls).toEqual(['refresh:start'])

    release()
    await done
    expect(calls).toEqual(['refresh:start', 'refresh:end', 'advance'])
  })
})
