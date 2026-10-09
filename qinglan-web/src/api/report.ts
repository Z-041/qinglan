import { httpGet } from '@shared/api/http'

import type { ReviewTargetType } from '@shared/types/review-common'
import type { MyReportState } from '@/types/report'

/**
 * 当前登录用户在某个目标上的举报状态（学生端详情页展示处理进度、并据此提前禁用重复举报）。
 *
 * 仅本人可见：后端按 `reporterId = 当前用户` 查询，响应不含任何他人举报信息（不变量 #1）。
 * 需登录——游客调用会拿到 401，由 http 拦截器统一处理。
 *
 * 本文件是**学生端专属**封装：与管理端共享的举报/申诉（`@shared/api/report`）不同，
 * 该查询只有学生端详情页使用，故不下沉到 shared。
 */
export function fetchMyReportState(
  targetType: ReviewTargetType,
  targetId: number,
): Promise<MyReportState> {
  return httpGet<MyReportState>('/reports/mine', { targetType, targetId })
}
