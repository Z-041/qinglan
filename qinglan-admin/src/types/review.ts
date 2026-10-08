/**
 * 管理端审核体系的类型（举报队列 / 审核上下文 / 处置 / 复审）。
 *
 * 学生端用到的 `ReportReason`、`ReviewTargetType` 与举报/申诉请求体已切到
 * `@shared/types/review-common`——因为共享的 `useUserLabels` 依赖那两个枚举把理由码映射成中文。
 * 本文件只保留管理端专属的部分。
 */

import type { ReportReason, ReviewTargetType } from '@shared/types/review-common'

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
  /**
   * 举报人填写的补充说明（已去重、只含非空项）。
   * 理由选「其他」时它是唯一线索，此前只入库不出参、管理端读不到。
   */
  details: string[]
}

/**
 * 与后端 review/dto/PendingPostItemResponse 一一对应（管理端待审帖子队列）。
 * 不含作者字段——作者身份不出队列（不变量 #1 引申）。
 */
export interface AdminPendingPostItem {
  /** 处置接口（approve / reject）与上下文面板所需的目标 id */
  postId: number
  preview: string
  /** 帖子当前状态码（PENDING / REVIEWING），文案映射见 useUserLabels.targetStatusLabel */
  targetStatus: string
  /** 提交审核时间（发布与提交同刻），据它展示等待时长 */
  submittedAt: string
  /** 待处理举报数：新帖通常为 0，非 0 说明该帖同时被举报，是排队时的重要线索 */
  reportCount: number
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
