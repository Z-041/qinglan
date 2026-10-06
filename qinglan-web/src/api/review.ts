import { httpGet, httpPost } from './http'

import type { CursorPage } from '@/types/api'
import type {
  AdminAppealItem,
  AdminQueueItem,
  AdminReviewContext,
  CreateReportRequest,
  DismissReportsPayload,
  ReasonPayload,
  ReviewTargetType,
  SubmitAppealRequest,
  TakeDownPayload,
} from '@/types/review'

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

// ------------------------------------------------------------------ 管理端

/** 举报队列：按举报数降序 + 等待时长升序，游标分页 */
export function fetchAdminReviewQueue(cursor: string | null): Promise<CursorPage<AdminQueueItem>> {
  return httpGet<CursorPage<AdminQueueItem>>('/admin/review-queue', { cursor })
}

/** 审核上下文：按 target 反查，队列不暴露作者与举报人身份（spec 7.3） */
export function fetchAdminReviewContext(
  targetType: ReviewTargetType,
  targetId: number,
): Promise<AdminReviewContext> {
  return httpGet<AdminReviewContext>('/admin/review-context', { targetType, targetId })
}

/** 待复审申诉队列：FIFO 游标分页；响应不含申诉人身份 */
export function fetchAdminAppeals(cursor: string | null): Promise<CursorPage<AdminAppealItem>> {
  return httpGet<CursorPage<AdminAppealItem>>('/admin/appeals', { cursor })
}

export function approvePost(postId: number): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/approve`)
}

export function rejectPost(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/reject`, payload)
}

export function takeDownPost(postId: number, payload: TakeDownPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/take-down`, payload)
}

/** 恢复可见；申诉通过走后端 AppealService，不直接调本接口 */
export function restorePost(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/restore`, payload)
}

export function hidePostContact(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/hide-contact`, payload)
}

/** 撤销隐藏：纠正「隐藏」的误判，联系方式恢复为认证用户可见 */
export function showPostContact(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/show-contact`, payload)
}

export function removeComment(commentId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/comments/${commentId}/remove`, payload)
}

/** 驳回举报，按目标一次结清 */
export function dismissReports(payload: DismissReportsPayload): Promise<void> {
  return httpPost<void>('/admin/reports/dismiss', payload)
}

/** 维持下架：确认举报成立并结案该帖全部待处理举报（不改内容状态） */
export function upholdReports(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/uphold-reports`, payload)
}

export function acceptAppeal(appealId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/appeals/${appealId}/accept`, payload)
}

export function rejectAppeal(appealId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/appeals/${appealId}/reject`, payload)
}
