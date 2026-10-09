import { computed, ref } from 'vue'

import { useCursorList } from '@shared/composables/useCursorList'

import { fetchAdminReviewQueue } from '@/api/review'
import { createQueueFilters, type AdminQueueItem, type ReviewQueueFilters } from '@/types/review'

/**
 * 首报距今超过该小时数即视为超时，行内标红。
 * 后端 qinglan.review 只有 autoTakeDownThreshold 与 appealWindow，没有等待时长阈值，
 * 不为此新增后端配置（spec 5.4），故定为前端常量。
 */
export const REPORT_OVERDUE_HOURS = 24

const MILLIS_PER_HOUR = 3_600_000

/** 首报时间是否已超时。now 可注入，便于测试 */
export function isReportOverdue(firstReportedAt: string, now: number = Date.now()): boolean {
  return now - new Date(firstReportedAt).getTime() > REPORT_OVERDUE_HOURS * MILLIS_PER_HOUR
}

/** 深拷贝一份筛选快照：`reasons` 是数组，浅拷贝会与后续变更共享引用 */
function snapshotFilters(filters: ReviewQueueFilters): ReviewQueueFilters {
  return { ...filters, reasons: [...filters.reasons] }
}

function sameFilters(a: ReviewQueueFilters, b: ReviewQueueFilters): boolean {
  return (
    a.targetType === b.targetType &&
    a.overdueOnly === b.overdueOnly &&
    a.sort === b.sort &&
    a.reasons.length === b.reasons.length &&
    a.reasons.every((reason, index) => reason === b.reasons[index])
  )
}

/**
 * 管理端举报队列：游标分页（状态机收在 {@link useCursorList}）+ 筛选 / 排序 + 刷新。
 *
 * <p>筛选与排序**全部由后端负责**（进 SQL），前端只负责把条件作为查询参数下发并重置游标；
 * 切换排序会改变游标格式，故 sort 与 cursor 必须成对出现。</p>
 *
 * <p>「已应用筛选」快照与列表同生共死：列表里是旧条件的结果、游标也属于旧结果集，
 * 若在筛选已改但未重查时继续「加载更多」，会把两个条件的数据拼在一起。续页闸门交给
 * {@link useCursorList} 的 `canLoadMore`；轮询探针也据它比对，否则筛选后的列表会被误判成
 * 「有新内容」而反复刷新。</p>
 */
export function useReviewQueue() {
  const filters = ref<ReviewQueueFilters>(createQueueFilters())

  /** 上一次**成功加载的第一页**所用的筛选快照；null 表示还没成功加载过 */
  const appliedFilters = ref<ReviewQueueFilters | null>(null)

  /** 筛选已改但未重查：此时列表与游标仍是旧条件，禁止续页、轮询也不得据新条件比对 */
  const filtersDirty = computed(
    () => appliedFilters.value !== null && !sameFilters(appliedFilters.value, filters.value),
  )

  /** 本次取页要用的筛选快照：每次翻页/刷新时重取，列表实际对应的即是它 */
  let requestFilters = snapshotFilters(filters.value)

  const list = useCursorList<AdminQueueItem>({
    fetchPage: (cursor) => {
      requestFilters = snapshotFilters(filters.value)
      return fetchAdminReviewQueue({ ...requestFilters, cursor })
    },
    canLoadMore: () => !filtersDirty.value,
    // 只有成功应用了第一页，才算「当前筛选已加载」
    onApplied: () => {
      appliedFilters.value = requestFilters
    },
  })

  /** 当前「列表实际对应的」筛选条件：已加载过就用快照，否则用当前表单值 */
  function effectiveFilters(): ReviewQueueFilters {
    return appliedFilters.value ?? filters.value
  }

  /**
   * 轮询探针：只取首屏的行键用于「是否有新内容」比对。
   * **必须带当前筛选参数**——否则筛选后的列表会被误判成有新内容而反复刷新。
   * 不写列表、不动游标，故与 `refresh` 并行发请求也不会打架。
   */
  async function headKeys(): Promise<string[]> {
    const page = await fetchAdminReviewQueue({ ...effectiveFilters(), cursor: null })
    return page.items.map((row) => `${row.targetType}-${row.targetId}`)
  }

  return {
    filters,
    filtersDirty,
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    /** 处置成功后重拉第一页（spec 5.5：局部刷新，不整页 reload） */
    refresh: list.refresh,
    loadMore: list.loadMore,
    headKeys,
  }
}
