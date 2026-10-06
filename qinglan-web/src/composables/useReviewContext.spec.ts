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
})
