import { ref } from 'vue'

import { fetchAdminReviewQueue } from '@/api/review'
import { useFilteredCursorList } from './useFilteredCursorList'
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

/**
 * 管理端举报队列：游标分页 + 筛选 / 排序 + 刷新。
 *
 * <p>筛选与排序**全部由后端负责**（进 SQL），前端只负责把条件作为查询参数下发并重置游标；
 * 切换排序会改变游标格式，故 sort 与 cursor 必须成对出现。</p>
 *
 * <p>「筛选已改但未重查」的记账与续页闸门收在 {@link useFilteredCursorList}；本 composable 只管
 * 筛选表单与轮询探针。</p>
 */
export function useReviewQueue() {
  const filters = ref<ReviewQueueFilters>(createQueueFilters())

  const { items, loading, loadingMore, hasMore, failed, refresh, loadMore, filtersDirty, effectiveFilters } =
    useFilteredCursorList<AdminQueueItem, ReviewQueueFilters>({
      filters,
      fetchPage: (requestFilters, cursor) => fetchAdminReviewQueue({ ...requestFilters, cursor }),
    })

  /**
   * 轮询探针：只取首屏的行键用于「是否有新内容」比对。
   * **必须带当前筛选参数**（用 effectiveFilters：已加载过就是列表实际对应的那份）——否则筛选后的
   * 列表会被误判成有新内容而反复刷新。不写列表、不动游标，故与 `refresh` 并行发请求也不会打架。
   */
  async function headKeys(): Promise<string[]> {
    const page = await fetchAdminReviewQueue({ ...effectiveFilters(), cursor: null })
    return page.items.map((row) => `${row.targetType}-${row.targetId}`)
  }

  return {
    filters,
    filtersDirty,
    items,
    loading,
    loadingMore,
    hasMore,
    failed,
    /** 处置成功后重拉第一页（spec 5.5：局部刷新，不整页 reload） */
    refresh,
    loadMore,
    headKeys,
  }
}
