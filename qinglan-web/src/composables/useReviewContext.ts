import { ref } from 'vue'

import { fetchAdminReviewContext } from '@/api/review'
import type { AdminReviewContext, ReviewTargetType } from '@/types/review'

export interface ReviewTarget {
  targetType: ReviewTargetType
  targetId: number
}

/**
 * 上下文面板的数据源：按 target 反查。
 * 队列响应刻意不含作者与举报人身份，作者信息只能这样按目标取（spec 7.3）。
 */
export function useReviewContext() {
  const target = ref<ReviewTarget | null>(null)
  const context = ref<AdminReviewContext | null>(null)
  const loading = ref(false)

  /**
   * 请求序号。快速点选不同举报行时会有多个上下文请求在飞，**先发的不一定先到**：
   * 没有它的话，面板会显示 A 的作者信用分与举报数，而 target 已经是 B——
   * 管理员据此判断，等于「看着 A 的数据处置 B」。
   */
  let requestSeq = 0

  async function open(next: ReviewTarget): Promise<void> {
    const seq = ++requestSeq
    target.value = next
    context.value = null
    loading.value = true
    try {
      const result = await fetchAdminReviewContext(next.targetType, next.targetId)
      // 过期响应直接丢弃，绝不写进面板
      if (seq === requestSeq) {
        context.value = result
      }
    } catch {
      // 错误已由拦截器提示
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  function close(): void {
    // 作废在飞的请求：否则关掉面板后，晚到的响应还会把 context 写回来
    requestSeq += 1
    target.value = null
    context.value = null
    loading.value = false
  }

  /** 处置完成后刷新面板（信用分、举报数等会变） */
  async function reload(): Promise<void> {
    if (target.value) {
      await open(target.value)
    }
  }

  return { target, context, loading, open, close, reload }
}
