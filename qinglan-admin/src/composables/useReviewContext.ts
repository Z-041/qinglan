import { ref } from 'vue'

import { fetchAdminReviewContext, fetchAdminReviewHistory } from '@/api/review'
import type { ReviewTargetType } from '@shared/types/review-common'
import type { AdminReviewContext, AdminReviewHistory } from '@/types/review'

export interface ReviewTarget {
  targetType: ReviewTargetType
  targetId: number
}

/**
 * 上下文面板的数据源：按 target 反查「概况」与「历史」两份数据。
 *
 * 队列响应刻意不含作者与举报人身份，作者信息只能这样按目标取（spec 7.3）；
 * 历史只回 ID / 状态 / 动作（不变量 #1 / #3）。
 *
 * 两份数据独立请求、各自失败各自置错，互不牵连：概况失败不应连带把历史 Tab 也标成失败。
 */
export function useReviewContext() {
  const target = ref<ReviewTarget | null>(null)
  const context = ref<AdminReviewContext | null>(null)
  const history = ref<AdminReviewHistory | null>(null)
  const loading = ref(false)
  const historyLoading = ref(false)
  /** 概况请求是否失败过。面板据此把「加载失败」与空白区分开，而不是留一片空抽屉 */
  const failed = ref(false)
  /** 历史请求是否失败过 */
  const historyFailed = ref(false)

  /**
   * 请求序号。快速点选不同举报行时会有多个上下文请求在飞，**先发的不一定先到**：
   * 没有它的话，面板会显示 A 的作者信用分与举报数，而 target 已经是 B——
   * 管理员据此判断，等于「看着 A 的数据处置 B」。
   */
  let requestSeq = 0

  async function loadContext(next: ReviewTarget, seq: number): Promise<void> {
    try {
      const result = await fetchAdminReviewContext(next.targetType, next.targetId)
      // 过期响应直接丢弃，绝不写进面板
      if (seq === requestSeq) {
        context.value = result
      }
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

  async function loadHistory(next: ReviewTarget, seq: number): Promise<void> {
    try {
      const result = await fetchAdminReviewHistory(next.targetType, next.targetId)
      if (seq === requestSeq) {
        history.value = result
      }
    } catch {
      if (seq === requestSeq) {
        historyFailed.value = true
      }
    } finally {
      if (seq === requestSeq) {
        historyLoading.value = false
      }
    }
  }

  async function open(next: ReviewTarget): Promise<void> {
    const seq = ++requestSeq
    target.value = next
    context.value = null
    history.value = null
    failed.value = false
    historyFailed.value = false
    loading.value = true
    historyLoading.value = true
    await Promise.all([loadContext(next, seq), loadHistory(next, seq)])
  }

  function close(): void {
    // 作废在飞的请求：否则关掉面板后，晚到的响应还会把 context 写回来
    requestSeq += 1
    target.value = null
    context.value = null
    history.value = null
    failed.value = false
    historyFailed.value = false
    loading.value = false
    historyLoading.value = false
  }

  /** 处置完成后刷新面板（信用分、举报数、处置流水等会变） */
  async function reload(): Promise<void> {
    if (target.value) {
      await open(target.value)
    }
  }

  return {
    target,
    context,
    history,
    loading,
    historyLoading,
    failed,
    historyFailed,
    open,
    close,
    reload,
  }
}
