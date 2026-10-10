import { computed, ref, type Ref } from 'vue'

import { useCursorList, type CursorList } from '@shared/composables/useCursorList'
import type { CursorPage } from '@shared/types/api'

/** 深拷贝一份筛选快照：筛选对象含数组（如 reasons），浅拷贝会与后续变更共享引用 */
function copyFilters<F>(filters: F): F {
  return JSON.parse(JSON.stringify(filters)) as F
}

export interface FilteredCursorList<T, F> extends CursorList<T> {
  /** 筛选已改但未重查：此时列表与游标仍是旧条件，续页闸门据此关闭 */
  filtersDirty: Ref<boolean>
  /** 列表实际对应的筛选：已加载过用快照，否则用当前表单值（供轮询探针 / 导出复用） */
  effectiveFilters: () => F
}

/**
 * 带筛选的游标列表：在 {@link useCursorList} 之上叠加「筛选已改但未重查」的记账。
 *
 * <p>两处队列（举报队列 / 用户管理）原先各写一份 filtersDirty：一处手写 snapshot + 逐字段比对，
 * 一处用 JSON 串比对。统一到 JSON 串口径（通用，无需为每个筛选类型另写 equals），并集中处理两件
 * 容易漏掉的事：① 续页闸门——列表与游标仍属旧结果集时禁止「加载更多」，否则会把两个条件的数据
 * 拼在一起（重复行 / 漏行）；② 「列表实际对应的筛选」——已成功加载过用快照、否则用当前表单值，
 * 供轮询探针比对，避免筛选后的列表被误判成「有新内容」而反复刷新。</p>
 */
export function useFilteredCursorList<T, F>(options: {
  filters: Ref<F>
  fetchPage: (filters: F, cursor: string | null) => Promise<CursorPage<T>>
}): FilteredCursorList<T, F> {
  /** 上一次**成功加载第一页**所用的筛选快照；null 表示还没成功加载过 */
  const applied = ref<F | null>(null) as Ref<F | null>

  /** 本次请求实际使用的筛选快照：onApplied 用它记账，避免请求在途时筛选又变导致误记 */
  let requestFilters: F = copyFilters(options.filters.value)

  const filtersDirty = computed(
    () =>
      applied.value !== null &&
      JSON.stringify(applied.value) !== JSON.stringify(options.filters.value),
  )

  const list = useCursorList<T>({
    fetchPage: (cursor) => {
      requestFilters = copyFilters(options.filters.value)
      return options.fetchPage(requestFilters, cursor)
    },
    canLoadMore: () => !filtersDirty.value,
    // 只有成功应用了第一页，才算「当前筛选已加载」
    onApplied: () => {
      applied.value = requestFilters
    },
  })

  function effectiveFilters(): F {
    return applied.value ?? options.filters.value
  }

  return { ...list, filtersDirty, effectiveFilters }
}
