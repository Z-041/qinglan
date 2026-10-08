import { httpPost } from '@shared/api/http'
import type { CreateReportRequest, SubmitAppealRequest } from '@shared/types/review-common'

/**
 * 举报与申诉：由**学生端**发起、但与管理端的审核/复审读改同一批目标，
 * 故从原 `api/review.ts` 切到 shared（管理端专属的队列与处置接口留在 qinglan-admin）。
 */

/**
 * 举报帖子或评论。
 *
 * 同一用户对同一目标仅一次（不变量 #4）：重复举报后端回 409，前端不拦截，
 * 由拦截器把后端的提示原样弹给用户。
 */
export function createReport(payload: CreateReportRequest): Promise<void> {
  return httpPost<void>('/reports', payload)
}

/** 作者对已下架帖子申诉：每帖限一次、限下架后 7 日内 */
export function submitAppeal(payload: SubmitAppealRequest): Promise<void> {
  return httpPost<void>('/appeals', payload)
}
