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

  async function open(next: ReviewTarget): Promise<void> {
    target.value = next
    context.value = null
    loading.value = true
    try {
      context.value = await fetchAdminReviewContext(next.targetType, next.targetId)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loading.value = false
    }
  }

  function close(): void {
    target.value = null
    context.value = null
  }

  /** 处置完成后刷新面板（信用分、举报数等会变） */
  async function reload(): Promise<void> {
    if (target.value) {
      await open(target.value)
    }
  }

  return { target, context, loading, open, close, reload }
}
