/**
 * 举报与申诉中**双端共用**的类型（学生端发起举报/申诉，管理端读改同一批目标）。
 *
 * 从原 `types/review.ts` 切出：那一份同时含学生端与管理端内容，而 `useUserLabels`（共享）
 * 依赖这两个枚举把理由码映射成中文，故它们必须留在 shared，不能被任一应用独占。
 * 管理端专属的 `Admin*` 系列与各类 Payload 留在 qinglan-admin。
 */

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
