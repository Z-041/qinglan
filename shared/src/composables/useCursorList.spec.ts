import { describe, expect, it, vi } from 'vitest'

import { useCursorList, type FetchPage } from '@shared/composables/useCursorList'
import type { CursorPage } from '@shared/types/api'

/** 可手动决定何时兑现的 Promise，用于精确编排「并发 / 乱序到达」的时序 */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function page(items: string[], nextCursor: string | null): CursorPage<string> {
  return { items, nextCursor }
}

describe('useCursorList', () => {
  it('刷新成功：替换列表，按 nextCursor 判定 hasMore', async () => {
    const fetchPage = vi.fn<FetchPage<string>>().mockResolvedValue(page(['a', 'b'], 'c1'))
    const list = useCursorList({ fetchPage })

    await list.refresh()

    expect(fetchPage).toHaveBeenCalledWith(null)
    expect(list.items.value).toEqual(['a', 'b'])
    expect(list.hasMore.value).toBe(true)
    expect(list.failed.value).toBe(false)
    expect(list.loading.value).toBe(false)
  })

  it('续页：带上游标追加，末页后 hasMore 为假', async () => {
    const fetchPage = vi
      .fn<FetchPage<string>>()
      .mockResolvedValueOnce(page(['a'], 'c1'))
      .mockResolvedValueOnce(page(['b'], null))
    const list = useCursorList({ fetchPage })

    await list.refresh()
    await list.loadMore()

    expect(list.items.value).toEqual(['a', 'b'])
    expect(fetchPage).toHaveBeenNthCalledWith(1, null)
    expect(fetchPage).toHaveBeenNthCalledWith(2, 'c1')
    expect(list.hasMore.value).toBe(false)
  })

  it('末页后 loadMore 不再发请求', async () => {
    const fetchPage = vi.fn<FetchPage<string>>().mockResolvedValue(page(['a'], null))
    const list = useCursorList({ fetchPage })

    await list.refresh()
    await list.loadMore()

    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('首屏失败：清空列表、failed 置位、结束 loading', async () => {
    const fetchPage = vi
      .fn<FetchPage<string>>()
      .mockResolvedValueOnce(page(['a'], 'c1'))
      .mockRejectedValueOnce(new Error('boom'))
    const list = useCursorList({ fetchPage })

    await list.refresh()
    expect(list.items.value).toEqual(['a'])

    // 再次刷新失败：不残留上一次的内容与游标
    await list.refresh()
    expect(list.failed.value).toBe(true)
    expect(list.items.value).toEqual([])
    expect(list.hasMore.value).toBe(false)
    expect(list.loading.value).toBe(false)
  })

  it('续页失败不清空已加载内容，也不把首屏置为失败', async () => {
    const fetchPage = vi
      .fn<FetchPage<string>>()
      .mockResolvedValueOnce(page(['a'], 'c1'))
      .mockRejectedValueOnce(new Error('boom'))
    const list = useCursorList({ fetchPage })

    await list.refresh()
    await list.loadMore()

    expect(list.items.value).toEqual(['a'])
    expect(list.failed.value).toBe(false)
  })

  it('过期刷新响应被丢弃：只有最新一次请求能写状态', async () => {
    const first = deferred<CursorPage<string>>()
    const second = deferred<CursorPage<string>>()
    const fetchPage = vi
      .fn<FetchPage<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const list = useCursorList({ fetchPage })

    const p1 = list.refresh()
    const p2 = list.refresh()
    // 后发的先到，先发的后到
    second.resolve(page(['new'], null))
    await p2
    first.resolve(page(['old'], 'c-old'))
    await p1

    expect(list.items.value).toEqual(['new'])
    expect(list.hasMore.value).toBe(false)
  })

  it('过期续页响应被丢弃：刷新后到达的旧 loadMore 不追加', async () => {
    const stalePage = deferred<CursorPage<string>>()
    const freshPage = deferred<CursorPage<string>>()
    const fetchPage = vi
      .fn<FetchPage<string>>()
      .mockResolvedValueOnce(page(['a'], 'c1'))
      .mockReturnValueOnce(stalePage.promise)
      .mockReturnValueOnce(freshPage.promise)
    const list = useCursorList({ fetchPage })

    await list.refresh()
    const more = list.loadMore()
    const refreshed = list.refresh()
    freshPage.resolve(page(['fresh'], null))
    await refreshed
    stalePage.resolve(page(['stale'], null))
    await more

    expect(list.items.value).toEqual(['fresh'])
  })

  it('canLoadMore 为假时拒绝续页', async () => {
    const fetchPage = vi.fn<FetchPage<string>>().mockResolvedValue(page(['a'], 'c1'))
    let allow = false
    const list = useCursorList({ fetchPage, canLoadMore: () => allow })

    await list.refresh()
    await list.loadMore()
    expect(fetchPage).toHaveBeenCalledTimes(1)

    allow = true
    await list.loadMore()
    expect(fetchPage).toHaveBeenCalledTimes(2)
  })

  it('onApplied 只在第一页成功应用时回调一次', async () => {
    const onApplied = vi.fn()
    const fetchPage = vi
      .fn<FetchPage<string>>()
      .mockResolvedValueOnce(page(['a'], null))
      .mockRejectedValueOnce(new Error('boom'))
    const list = useCursorList({ fetchPage, onApplied })

    await list.refresh()
    expect(onApplied).toHaveBeenCalledTimes(1)

    // 失败不回调
    await list.refresh()
    expect(onApplied).toHaveBeenCalledTimes(1)
  })

  it('被更新的请求取代时不回调 onApplied', async () => {
    const onApplied = vi.fn()
    const first = deferred<CursorPage<string>>()
    const second = deferred<CursorPage<string>>()
    const fetchPage = vi
      .fn<FetchPage<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const list = useCursorList({ fetchPage, onApplied })

    const p1 = list.refresh()
    const p2 = list.refresh()
    second.resolve(page(['new'], null))
    await p2
    first.resolve(page(['old'], null))
    await p1

    expect(onApplied).toHaveBeenCalledTimes(1)
    expect(list.items.value).toEqual(['new'])
  })
})
