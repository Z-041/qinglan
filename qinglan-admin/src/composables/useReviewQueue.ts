import { computed, ref } from 'vue'

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
 * 管理端举报队列：游标分页 + 筛选 / 排序 + 刷新。
 *
 * <p>筛选与排序**全部由后端负责**（进 SQL），前端只负责把条件作为查询参数下发并重置游标；
 * 切换排序会改变游标格式，故 sort 与 cursor 必须成对出现。</p>
 *
 * <p>「已应用筛选」快照与列表同生共死：列表里是旧条件的结果、游标也属于旧结果集，
 * 若在筛选已改但未重查时继续「加载更多」，会把两个条件的数据拼在一起。轮询探针也据它比对，
 * 否则筛选后的列表会被误判成「有新内容」而反复刷新。</p>
 */
export function useReviewQueue() {
  const filters = ref<ReviewQueueFilters>(createQueueFilters())
  const items = ref<AdminQueueItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  /** 首屏是否失败过。用于把「加载失败」与「暂无待处理举报」分成两屏 */
  const failed = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /**
   * 请求序号。处置成功后 refresh 与用户点「刷新」可能同时在飞，**先发的不一定先到**：
   * 没有它的话，晚到的旧响应会把新结果覆盖掉。
   */
  let requestSeq = 0

  /** 上一次**成功加载的第一页**所用的筛选快照；null 表示还没成功加载过 */
  const appliedFilters = ref<ReviewQueueFilters | null>(null)

  /** 筛选已改但未重查：此时列表与游标仍是旧条件，禁止续页、轮询也不得据新条件比对 */
  const filtersDirty = computed(
    () => appliedFilters.value !== null && !sameFilters(appliedFilters.value, filters.value),
  )

  /** 当前「列表实际对应的」筛选条件：已加载过就用快照，否则用当前表单值 */
  function effectiveFilters(): ReviewQueueFilters {
    return appliedFilters.value ?? filters.value
  }

  function applyPage(page: { items: AdminQueueItem[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /** 处置成功后重拉第一页（spec 5.5：局部刷新，不整页 reload）；筛选变化后同样走这里 */
  async function refresh(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    // 先清干净再请求：否则本次失败时列表与游标会留在**旧筛选条件**的结果集上，
    // 此后点「加载更多」就会拿「新筛选 + 旧游标」去请求
    items.value = []
    cursor = null
    hasMore.value = false
    try {
      const request = snapshotFilters(filters.value)
      const page = await fetchAdminReviewQueue({ ...request, cursor: null })
      // 过期响应必须丢弃：处置后的 refresh 与用户点「刷新」可能同时在飞，先发的不一定先到
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, false)
      // 只有成功应用了第一页，才算「当前筛选已加载」
      appliedFilters.value = request
    } catch {
      // 错误已由拦截器提示；只有当前这次请求才有权把状态置为失败
      if (seq === requestSeq) {
        failed.value = true
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  async function loadMore(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value || filtersDirty.value) {
      return
    }
    // 记下发起时的序号：期间若刷新过（requestSeq 会 +1），这次的响应必须丢弃
    const seq = requestSeq
    loadingMore.value = true
    try {
      const page = await fetchAdminReviewQueue({ ...effectiveFilters(), cursor })
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, true)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loadingMore.value = false
    }
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
    items,
    loading,
    loadingMore,
    hasMore,
    failed,
    refresh,
    loadMore,
    headKeys,
  }
}
