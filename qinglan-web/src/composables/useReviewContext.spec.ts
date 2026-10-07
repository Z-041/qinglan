import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminReviewContext } from '@/api/review'
import { useReviewContext } from '@/composables/useReviewContext'
import type { AdminReviewContext } from '@/types/review'

vi.mock('@/api/review', () => ({ fetchAdminReviewContext: vi.fn() }))

const mockedFetchAdminReviewContext = vi.mocked(fetchAdminReviewContext)

function context(): AdminReviewContext {
  return {
    targetType: 'POST',
    targetId: 5,
    targetStatus: 'PENDING',
    authorCreditScore: 88,
    authorViolationCount: 2,
    reportCount: 3,
    distinctReporterCount: 3,
    repeatedReporterCount: 1,
    reasons: ['ABUSE'],
  }
}

/** 手动控制何时 resolve，用来构造「响应乱序」的场景 */
function deferredContext() {
  let resolve: (value: AdminReviewContext) => void = () => {}
  const promise = new Promise<AdminReviewContext>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

describe('useReviewContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('按 target 拉取上下文并结束 loading', async () => {
    mockedFetchAdminReviewContext.mockResolvedValue(context())

    const panel = useReviewContext()
    await panel.open({ targetType: 'POST', targetId: 5 })

    expect(mockedFetchAdminReviewContext).toHaveBeenCalledWith('POST', 5)
    expect(panel.context.value?.authorCreditScore).toBe(88)
    expect(panel.loading.value).toBe(false)
  })

  it('拉取失败时结束 loading 且 context 保持 null', async () => {
    mockedFetchAdminReviewContext.mockRejectedValue(new Error('网络异常'))

    const panel = useReviewContext()
    await expect(panel.open({ targetType: 'COMMENT', targetId: 6 })).resolves.toBeUndefined()

    expect(panel.context.value).toBeNull()
    expect(panel.loading.value).toBe(false)
  })

  it('关闭后清空 target 与 context', async () => {
    mockedFetchAdminReviewContext.mockResolvedValue(context())

    const panel = useReviewContext()
    await panel.open({ targetType: 'POST', targetId: 5 })
    panel.close()

    expect(panel.target.value).toBeNull()
    expect(panel.context.value).toBeNull()
  })

  it('reload 用当前 target 重拉；未打开时不发请求', async () => {
    mockedFetchAdminReviewContext.mockResolvedValue(context())

    const panel = useReviewContext()
    await panel.reload()
    expect(mockedFetchAdminReviewContext).not.toHaveBeenCalled()

    await panel.open({ targetType: 'POST', targetId: 5 })
    await panel.reload()

    expect(mockedFetchAdminReviewContext).toHaveBeenCalledTimes(2)
  })

  it('快速点选不同目标时晚到的旧响应不得写回面板（否则会看 A 的数据处置 B）', async () => {
    const first = deferredContext()
    const second = deferredContext()
    mockedFetchAdminReviewContext
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const panel = useReviewContext()
    const openA = panel.open({ targetType: 'POST', targetId: 5 })
    const openB = panel.open({ targetType: 'COMMENT', targetId: 6 })

    // 后发的 B 先返回
    second.resolve({ ...context(), targetType: 'COMMENT', targetId: 6, authorCreditScore: 60 })
    await openB
    // A 的响应晚到：面板必须仍是 B 的数据，否则管理员会对着 A 的信用分/举报数处置 B
    first.resolve({ ...context(), targetType: 'POST', targetId: 5, authorCreditScore: 88 })
    await openA

    expect(panel.target.value?.targetId).toBe(6)
    expect(panel.context.value?.targetId).toBe(6)
    expect(panel.context.value?.authorCreditScore).toBe(60)
    expect(panel.loading.value).toBe(false)
  })

  it('关闭面板后在飞的响应回来，不得把上下文写回', async () => {
    const pending = deferredContext()
    mockedFetchAdminReviewContext.mockReturnValue(pending.promise)

    const panel = useReviewContext()
    const opening = panel.open({ targetType: 'POST', targetId: 5 })
    panel.close()
    pending.resolve(context())
    await opening

    expect(panel.context.value).toBeNull()
    expect(panel.loading.value).toBe(false)
  })
})
