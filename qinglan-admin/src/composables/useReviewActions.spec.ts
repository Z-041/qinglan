import { beforeEach, describe, expect, it, vi } from 'vitest'

import { approvePost, takeDownPost } from '@/api/review'
import { useReviewActions } from '@/composables/useReviewActions'

vi.mock('@/api/review', () => ({
  approvePost: vi.fn(),
  rejectPost: vi.fn(),
  takeDownPost: vi.fn(),
  restorePost: vi.fn(),
  hidePostContact: vi.fn(),
  showPostContact: vi.fn(),
  removeComment: vi.fn(),
  dismissReports: vi.fn(),
  upholdReports: vi.fn(),
  acceptAppeal: vi.fn(),
  rejectAppeal: vi.fn(),
}))

const mockedApprovePost = vi.mocked(approvePost)
const mockedTakeDownPost = vi.mocked(takeDownPost)

describe('useReviewActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('成功后返回 true 并复位 submitting', async () => {
    mockedApprovePost.mockResolvedValue(undefined)

    const actions = useReviewActions()
    await expect(actions.approvePost(5)).resolves.toBe(true)

    expect(mockedApprovePost).toHaveBeenCalledWith(5)
    expect(actions.submitting.value).toBe(false)
  })

  it('请求在途时 submitting 为 true', async () => {
    let release: () => void = () => {}
    mockedApprovePost.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          release = resolve
        }),
    )

    const actions = useReviewActions()
    const pending = actions.approvePost(5)

    expect(actions.submitting.value).toBe(true)

    release()
    await pending
    expect(actions.submitting.value).toBe(false)
  })

  it('失败时返回 false 且不向外抛错（错误已由拦截器提示）', async () => {
    mockedTakeDownPost.mockRejectedValue(new Error('网络异常'))

    const actions = useReviewActions()
    const payload = { reasonCode: 'ABUSE' as const, detail: null, banDays: null }

    await expect(actions.takeDownPost(5, payload)).resolves.toBe(false)
    expect(actions.submitting.value).toBe(false)
  })
})
