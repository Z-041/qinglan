import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useFilteredCursorList } from './useFilteredCursorList'
import type { CursorPage } from '@shared/types/api'

interface Filters {
  q: string
  tags: string[]
}

function page(items: number[], nextCursor: string | null = null): CursorPage<number> {
  return { items, nextCursor }
}

describe('useFilteredCursorList', () => {
  it('成功加载后 filtersDirty 为假；改筛选即置真，重查后复位', async () => {
    const filters = ref<Filters>({ q: '', tags: [] })
    const fetchPage = vi.fn(async () => page([1]))
    const list = useFilteredCursorList<number, Filters>({ filters, fetchPage })

    // 还没成功加载过：不算「脏」（此时列表本就空，没有旧结果集可言）
    expect(list.filtersDirty.value).toBe(false)

    await list.refresh()
    expect(list.filtersDirty.value).toBe(false)

    filters.value = { q: 'x', tags: ['a'] }
    expect(list.filtersDirty.value).toBe(true)

    await list.refresh()
    expect(list.filtersDirty.value).toBe(false)
  })

  it('filtersDirty 时禁止续页，避免把两个条件的数据拼在一起', async () => {
    const filters = ref<Filters>({ q: '', tags: [] })
    const fetchPage = vi.fn(async () => page([1], 'cursor-1'))
    const list = useFilteredCursorList<number, Filters>({ filters, fetchPage })

    await list.refresh()
    expect(list.hasMore.value).toBe(true)

    filters.value = { q: 'x', tags: [] }
    await list.loadMore()

    // 只应发生 refresh 那一次请求，loadMore 被闸门拦下
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('effectiveFilters 用「请求时」的快照：在途改筛选不会被误记为已应用', async () => {
    const filters = ref<Filters>({ q: 'a', tags: [] })
    let release: (value: CursorPage<number>) => void = () => {}
    const fetchPage = vi.fn(
      () =>
        new Promise<CursorPage<number>>((resolve) => {
          release = resolve
        }),
    )
    const list = useFilteredCursorList<number, Filters>({ filters, fetchPage })

    // 尚未成功加载：回退到当前表单值
    expect(list.effectiveFilters()).toEqual({ q: 'a', tags: [] })

    const pending = list.refresh()
    // 请求在途时用户又改了筛选
    filters.value = { q: 'b', tags: [] }
    release(page([1]))
    await pending

    // 已应用快照仍是发起请求时的那份，且当前筛选被判定为「脏」
    expect(list.effectiveFilters()).toEqual({ q: 'a', tags: [] })
    expect(list.filtersDirty.value).toBe(true)
  })
})
