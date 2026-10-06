import { ref } from 'vue'

import { createReport } from '@/api/review'
import type { ReportReason, ReviewTargetType } from '@/types/review'

/** 举报对象：目标类型 + id + 弹层文案（让用户确认自己在举报什么） */
export interface ReportTarget {
  targetType: ReviewTargetType
  targetId: number
  label: string
}

/**
 * 举报弹层状态与提交。
 *
 * 请求失败由 http 拦截器统一弹窗，本 composable 只负责成功后的状态推进；
 * 成功提示留给调用方，避免「弹层 + 提示」两处文案分散。
 */
export function useReport() {
  const target = ref<ReportTarget | null>(null)
  const submitting = ref(false)

  function open(next: ReportTarget): void {
    target.value = next
  }

  function close(): void {
    target.value = null
  }

  /** 提交举报；返回是否成功，成功即关闭弹层 */
  async function submit(reasonCode: ReportReason, detail: string): Promise<boolean> {
    const current = target.value
    if (!current) {
      return false
    }
    submitting.value = true
    try {
      await createReport({
        targetType: current.targetType,
        targetId: current.targetId,
        reasonCode,
        detail: detail.trim() || null,
      })
      close()
      return true
    } catch {
      // 错误已由拦截器提示
      return false
    } finally {
      submitting.value = false
    }
  }

  return { target, submitting, open, close, submit }
}
