import { describe, expect, it, vi } from 'vitest'

import { useLikeToggle, type LikeState } from './useLikeToggle'

const { toggleLike } = useLikeToggle()

describe('useLikeToggle', () => {
  it('未赞时点 like，回写后端返回的 liked 与计数', async () => {
    const target: LikeState = { liked: false, likeCount: 3 }
    const like = vi.fn().mockResolvedValue({ liked: true, likeCount: 4 })
    const unlike = vi.fn()

    await toggleLike(target, 7, like, unlike)

    expect(like).toHaveBeenCalledWith(7)
    expect(unlike).not.toHaveBeenCalled()
    expect(target).toEqual({ liked: true, likeCount: 4 })
  })

  it('已赞时点 unlike', async () => {
    const target: LikeState = { liked: true, likeCount: 4 }
    const like = vi.fn()
    const unlike = vi.fn().mockResolvedValue({ liked: false, likeCount: 3 })

    await toggleLike(target, 7, like, unlike)

    expect(unlike).toHaveBeenCalledWith(7)
    expect(like).not.toHaveBeenCalled()
    expect(target).toEqual({ liked: false, likeCount: 3 })
  })

  it('请求失败时本地状态保持不变（不做乐观更新，无需回滚）', async () => {
    const target: LikeState = { liked: false, likeCount: 3 }
    const like = vi.fn().mockRejectedValue(new Error('网络异常'))
    const unlike = vi.fn()

    await toggleLike(target, 7, like, unlike)

    expect(target).toEqual({ liked: false, likeCount: 3 })
  })
})
