import { httpGet, httpPost } from '@shared/api/http'

import type { CursorPage } from '@shared/types/api'
// ReviewTargetType 双端共用（学生端举报也用它），故来自 shared
import type { ReviewTargetType } from '@shared/types/review-common'
import type {
  AdminAppealItem,
  AdminPendingPostItem,
  AdminQueueItem,
  AdminReviewContext,
  AdminReviewHistory,
  DismissReportsPayload,
  ProposalDispositionPayload,
  ReasonPayload,
  ReviewQueueParams,
} from '@/types/review'
import type { AdminTodoCounts } from '@/types/admin'

// createReport / submitAppeal 由学生端发起，已切到 `@shared/api/report`；
// 本文件只保留管理端的队列与处置接口。

// ------------------------------------------------------------------ 管理端

/**
 * 举报队列：游标分页 + 筛选（理由多选 / 目标类型 / 只看超时）+ 排序。
 *
 * <p>筛选与排序全部进后端查询参数（不前端本地过滤），否则游标分页口径会被打破。
 * **不同 sort 对应不同的游标格式**，故 sort 始终随请求下发，cursor 原样回传、前端不解析。</p>
 *
 * <p>理由用逗号连接下发：Spring 会把逗号分隔的字符串绑成 `List<ReportReason>`，
 * 避免依赖 axios 的数组序列化格式（`reasons[]=` 与后端的 `reasons` 参数名对不上）。</p>
 */
export function fetchAdminReviewQueue(
  params: ReviewQueueParams,
): Promise<CursorPage<AdminQueueItem>> {
  return httpGet<CursorPage<AdminQueueItem>>('/admin/review-queue', {
    cursor: params.cursor,
    reasons: params.reasons.length > 0 ? params.reasons.join(',') : null,
    targetType: params.targetType,
    overdueOnly: params.overdueOnly ? true : null,
    sort: params.sort,
  })
}

/** 待办计数：顶栏铃铛轮询的轻量接口，按权限点裁剪（无权限的分组字段直接缺失） */
export function fetchAdminTodoCounts(): Promise<AdminTodoCounts> {
  return httpGet<AdminTodoCounts>('/admin/todo-counts')
}

/** 上下文面板增强：作者最近动态 + 目标处置历史（只含 ID / 状态 / 动作，不含身份） */
export function fetchAdminReviewHistory(
  targetType: ReviewTargetType,
  targetId: number,
): Promise<AdminReviewHistory> {
  return httpGet<AdminReviewHistory>('/admin/review-context/history', { targetType, targetId })
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

/**
 * 待审帖子队列：先审后发下放行新帖的唯一入口（举报队列按被举报目标聚合，新帖不在其中），
 * FIFO 游标分页；响应不含作者身份。
 */
export function fetchAdminPendingPosts(cursor: string | null): Promise<CursorPage<AdminPendingPostItem>> {
  return httpGet<CursorPage<AdminPendingPostItem>>('/admin/pending-posts', { cursor })
}

export function approvePost(postId: number): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/approve`)
}

export function rejectPost(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/reject`, payload)
}

/**
 * 下架：二期起改为**发起处置提案**（P5 决策 D5），返回提案号，由委员会表决通过后系统才真正下架。
 * 原 `banDays`（下架并封禁）已随接口移除，封禁改由用户管理页单独执行。
 */
export function takeDownPost(
  postId: number,
  payload: ProposalDispositionPayload,
): Promise<{ proposalId: number }> {
  return httpPost<{ proposalId: number }>(`/admin/posts/${postId}/take-down`, payload)
}

/** 恢复可见；申诉通过走后端 AppealService，不直接调本接口 */
export function restorePost(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/restore`, payload)
}

/** 隐藏联系方式：二期起改为发起提案（不变量 #2 的处置侧） */
export function hidePostContact(
  postId: number,
  payload: ProposalDispositionPayload,
): Promise<{ proposalId: number }> {
  return httpPost<{ proposalId: number }>(`/admin/posts/${postId}/hide-contact`, payload)
}

/** 撤销隐藏：纠正「隐藏」的误判，联系方式恢复为认证用户可见 */
export function showPostContact(postId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/posts/${postId}/show-contact`, payload)
}

/** 移除评论：二期起改为发起提案——评论先发后审，这是唯一的兜底处置手段 */
export function removeComment(
  commentId: number,
  payload: ProposalDispositionPayload,
): Promise<{ proposalId: number }> {
  return httpPost<{ proposalId: number }>(`/admin/comments/${commentId}/remove`, payload)
}

/** 驳回举报，按目标一次结清 */
export function dismissReports(payload: DismissReportsPayload): Promise<void> {
  return httpPost<void>('/admin/reports/dismiss', payload)
}

/** 维持下架：二期起改为发起提案（确认违规成立、结案该帖待处理举报） */
export function upholdReports(
  postId: number,
  payload: ProposalDispositionPayload,
): Promise<{ proposalId: number }> {
  return httpPost<{ proposalId: number }>(`/admin/posts/${postId}/uphold-reports`, payload)
}

export function acceptAppeal(appealId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/appeals/${appealId}/accept`, payload)
}

export function rejectAppeal(appealId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/appeals/${appealId}/reject`, payload)
}
