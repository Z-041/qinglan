import { httpPost } from './http'

import type { CreateReportRequest, SubmitAppealRequest } from '@/types/review'

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
