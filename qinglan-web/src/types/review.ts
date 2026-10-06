/**
 * 与后端 review/entity/ReviewTargetType 一一对应。
 * 帖子与评论是仅有的两类可举报目标。
 */
export type ReviewTargetType = 'POST' | 'COMMENT'

/**
 * 与后端 review/entity/ReportReason 一一对应。
 * 举报理由与管理员处置的违规类型共用同一套枚举，故文案映射在 `useUserLabels` 里只写一份。
 */
export type ReportReason =
  | 'ADVERTISEMENT'
  | 'PORNOGRAPHY'
  | 'ABUSE'
  | 'PRIVACY'
  | 'FRAUD'
  | 'ILLEGAL'
  | 'IRRELEVANT'
  | 'OTHER'

/**
 * 与后端 review/dto/CreateReportRequest 一一对应。
 * 举报人取自登录态，请求体里不带任何举报人字段（不变量 #1 引申）。
 */
export interface CreateReportRequest {
  targetType: ReviewTargetType
  targetId: number
  reasonCode: ReportReason
  detail?: string | null
}

/** 与后端 review/dto/SubmitAppealRequest 一一对应；作者对已下架帖子申诉，每帖限一次 */
export interface SubmitAppealRequest {
  postId: number
  reason: string
}

/** 与后端 review/dto/ReviewQueueItemResponse 一一对应（管理端举报队列） */
export interface AdminQueueItem {
  targetType: ReviewTargetType
  targetId: number
  preview: string
  /** 目标当前状态码（帖子 / 评论状态枚举名），文案映射见 useUserLabels.targetStatusLabel */
  targetStatus: string
  reportCount: number
  firstReportedAt: string
  reasons: ReportReason[]
}

/** 与后端 review/dto/AdminReviewContextResponse 一一对应；只含聚合值，不含任何人的身份 */
export interface AdminReviewContext {
  targetType: ReviewTargetType
  targetId: number
  targetStatus: string
  authorCreditScore: number
  authorViolationCount: number
  reportCount: number
  distinctReporterCount: number
  repeatedReporterCount: number
  reasons: ReportReason[]
}

/**
 * 与后端 review/dto/AdminAppealItemResponse 一一对应。
 * 不含申诉人字段（申诉人即作者，不变量 #1 引申）。
 */
export interface AdminAppealItem {
  /** 复审接口所需；界面不渲染这个数字（决策 6） */
  appealId: number
  targetType: ReviewTargetType
  targetId: number
  /** 目标已被删除时后端返回 null */
  preview: string | null
  targetStatus: string | null
  /** 原下架理由（post.review_reason），供复审人判断原判是否合理 */
  takenDownReason: string | null
  appealReason: string
  appealedAt: string
}

/** 与后端 review/dto/TakeDownPostRequest 一一对应；banDays 非空即「下架并封禁」 */
export interface TakeDownPayload {
  reasonCode: ReportReason
  detail?: string | null
  banDays?: number | null
}

/** 与后端 review/dto/DismissReportsRequest 一一对应 */
export interface DismissReportsPayload {
  targetType: ReviewTargetType
  targetId: number
  reason?: string | null
}

/** 仅含理由的处置请求体，与后端 user/dto/ReasonRequest 一一对应 */
export interface ReasonPayload {
  reason: string
}

/**
 * 管理端处置动作，与 ReviewActionBar 的按钮一一对应。
 * 放在 types 里而不是组件的 `<script setup>` 里：SFC 的 script setup 不能导出类型。
 */
export type ReviewAction =
  | 'approve'
  | 'reject'
  | 'take-down'
  | 'restore'
  | 'uphold'
  | 'hide-contact'
  | 'show-contact'
  | 'remove-comment'
  | 'dismiss'
