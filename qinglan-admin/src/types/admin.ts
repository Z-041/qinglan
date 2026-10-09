import type { AnnouncementLevel } from '@shared/types/announcement'
import type { ReportReason } from '@shared/types/review-common'
import type { UserProfile, UserRole, UserStatus } from '@shared/types/user'
import type { AdminPermission } from '@/types/permission'

/**
 * 公告等级直接复用学生端公告的类型（单一来源），管理端不再另造同枚举。
 * 决策 D14 相关的 DTO 差异只在字段形态，枚举取值两边一致。
 */
export type { AnnouncementLevel }

/** 与后端 user/dto/AdminMeResponse 一一对应（P5 7.2）：角色 + 权限点，前端据此渲染 */
export interface AdminMe {
  userId: number
  role: UserRole
  permissions: AdminPermission[]
}

/**
 * 与后端 user/dto/AssignableRole 一一对应。
 * 刻意不含 SUPER_ADMIN：超管唯一且是初始账号，前端也不该提供这个选项。
 */
export type AssignableRole = 'ADMIN' | 'REVIEWER' | 'STUDENT'

/** 与后端 user/dto/ChangeUserRoleRequest 一一对应 */
export interface ChangeUserRolePayload {
  role: AssignableRole
  reason: string
}

/** 与后端 common/audit/AuditTargetType 一致 */
export type AuditTargetType = 'USER' | 'POST' | 'COMMENT' | 'ANNOUNCEMENT'

/** 审计目标类型 → 中文（审计日志页与上下文面板共用） */
export const AUDIT_TARGET_TYPE_LABELS: Record<AuditTargetType, string> = {
  USER: '用户',
  POST: '帖子',
  COMMENT: '评论',
  ANNOUNCEMENT: '公告',
}

/**
 * 审计动作码 → 中文。与后端 common/audit/AuditAction 一一对应。
 * 未知码退化为原码展示（版本不同步时宁可显示码，也不要渲染出空）。
 */
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  BAN: '封禁用户',
  UNBAN: '解封用户',
  CREDIT_ADJUST: '调整信用分',
  POST_APPROVE: '放行帖子',
  POST_REJECT: '驳回帖子',
  POST_TAKEDOWN: '下架帖子',
  POST_AUTO_TAKEDOWN: '自动下架（举报达阈值）',
  POST_RESTORE: '恢复帖子可见',
  POST_HIDE_CONTACT: '隐藏联系方式',
  POST_SHOW_CONTACT: '恢复联系方式',
  COMMENT_REMOVE: '移除评论',
  REPORT_DISMISS: '驳回举报',
  REPORT_UPHOLD: '维持下架',
  APPEAL_REVIEW: '复审申诉',
  ROLE_GRANT: '下放管理权限',
  ROLE_REVOKE: '收回管理权限',
  ANNOUNCEMENT_CREATE: '创建公告草稿',
  ANNOUNCEMENT_PUBLISH: '发布公告',
  ANNOUNCEMENT_REVOKE: '撤回公告',
  /** 查看联系方式：由普通用户浏览触发，量级最大，排在最后 */
  CONTACT_VIEW: '查看联系方式',
}

/** 取审计动作文案；未知码退化为原码，不渲染出空 */
export function auditActionLabel(action: string): string {
  return AUDIT_ACTION_LABELS[action] ?? action
}

/**
 * 与后端 review/dto/AdminAuditLogItemResponse 一一对应（P5 7.5）。
 *
 * 不含任何身份明文：操作人只以 id 出现（不变量 #3，超管也不例外）。
 */
export interface AdminAuditLogItem {
  id: number
  targetType: AuditTargetType
  targetId: number
  /** 审计动作码（AuditAction 枚举名），界面按码映射中文 */
  action: string
  operatorId: number
  reason: string | null
  createdAt: string
}

/** 审计日志查询条件：全部可选，空串表示不限 */
export interface AuditLogQuery {
  cursor?: string | null
  action?: string | null
  /** ISO-8601 字符串，后端按 Instant 解析 */
  from?: string | null
  to?: string | null
}

/** 与后端 user/dto/AdminUserQuery 一一对应：全部可选且可组合，游标原样回传 */
export interface AdminUserQuery {
  cursor?: string | null
  status?: UserStatus | null
  role?: UserRole | null
  minCredit?: number | null
  maxCredit?: number | null
  /** ISO-8601 字符串，后端按 Instant 解析 */
  createdFrom?: string | null
  createdTo?: string | null
}

/** 与后端 review/dto/AdminStatsResponse.DailyCount 一一对应 */
export interface DailyCount {
  date: string
  count: number
}

/** 与后端 review/dto/AdminStatsResponse.ReasonCount 一一对应：举报按理由分布的一项 */
export interface ReasonCount {
  reason: ReportReason
  count: number
}

/** 与后端 review/dto/AdminStatsResponse.LatencyStats 一一对应：处置时效分位数（单位秒） */
export interface LatencyStats {
  p50Seconds: number
  p90Seconds: number
  sampleSize: number
}

/** 与后端 review/dto/AdminStatsResponse.ProposalStats 一一对应 */
export interface ProposalStats {
  approved: number
  rejected: number
  escalated: number
  /** 通过率 = approved / (approved + rejected)；分母为 0 时为 0 */
  approvalRate: number
}

/** 与后端 review/dto/AdminStatsResponse.ModeratorRank 一一对应：只有 operatorId 与计数 */
export interface ModeratorRank {
  operatorId: number
  count: number
}

/** 与后端 review/dto/AdminStatsResponse 一一对应 */
export interface AdminStats {
  content: {
    totalPosts: number
    pendingPosts: number
    takenDownPosts: number
    todayNewPosts: number
  }
  moderation: {
    pendingReports: number
    pendingAppeals: number
    todayHandled: number
    /** 固定 7 项、日期升序，无数据补 0（后端保证，前端不再补齐） */
    last7DaysHandled: DailyCount[]
  }
  users: {
    totalUsers: number
    bannedUsers: number
    todayNewUsers: number
  }
  /** 处置趋势：近 7 日与近 30 日两组，均按日期升序、无数据补 0 */
  trends: {
    last7Days: DailyCount[]
    last30Days: DailyCount[]
  }
  /** 举报按理由分布（举报总数，不限状态） */
  reportReasons: ReasonCount[]
  /** 处置时效分位数（近 30 天样本） */
  dispositionLatency: LatencyStats
  /** 提案表决概况 */
  proposals: ProposalStats
  /** 审核员处置量排行；只有 operatorId 与计数，不含身份明文 */
  moderatorRanking: ModeratorRank[]
}

/**
 * 与后端 review/dto/AdminTodoCountsResponse 一一对应（阶段 B 顶栏铃铛）。
 *
 * **无权限的分组字段直接缺失（不是 0）**，故四个字段全部可选；读取时按可选处理——
 * 只有「字段存在」才代表「有权限且这是真实计数」。
 */
export interface AdminTodoCounts {
  pendingPosts?: number
  pendingReports?: number
  proposable?: number
  escalated?: number
}

/** 与后端 user/dto/BanUserRequest 一一对应 */
export interface BanUserPayload {
  days: number
  reason: string
}

/** 与后端 user/dto/CreditAdjustRequest 一一对应 */
export interface CreditAdjustPayload {
  delta: number
  reason: string
}

/** 用户列表的一行就是既有 UserProfileResponse，不另造同字段类型 */
export type AdminUserItem = UserProfile

/**
 * 用户管理筛选表单的状态。
 *
 * 表单控件绑的是字符串：空串统一表示「不限」。规范化集中交给
 * `useUserManage` 的 `toQuery`，视图与 composable 不各转一遍。
 */
export interface UserFilterState {
  /** 空串 = 不限 */
  status: UserStatus | ''
  /** 空串 = 不限 */
  role: UserRole | ''
  /** 空串 = 不限；输入为字符串，提交时转数字 */
  minCredit: string
  maxCredit: string
  /** datetime-local 的值（本地时间，形如 2026-10-01T08:00），空串 = 不限 */
  createdFrom: string
  createdTo: string
}

/** 与后端 announcement/entity/AnnouncementStatus 一致（P4 决策 D7：撤回后不可再发布） */
export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'REVOKED'

/**
 * 与后端 announcement/dto/AdminAnnouncementResponse 一一对应。
 *
 * 生效区间为 `[startAt, endAt]`，`endAt` 为 null 表示长期有效。
 * `minReadSeconds` / `requireScrollToEnd` / `requireConfirm` 是强制阅读的三个条件（不变量 #6）。
 */
export interface AdminAnnouncementItem {
  id: number
  title: string
  level: AnnouncementLevel
  status: AnnouncementStatus
  startAt: string
  endAt: string | null
  minReadSeconds: number
  requireScrollToEnd: boolean
  requireConfirm: boolean
  publishedAt: string | null
  revokedAt: string | null
  createdAt: string
}

/**
 * 与后端 announcement/dto/CreateAnnouncementRequest 一一对应。
 *
 * `startAt` / `endAt` 是 ISO-8601 UTC 字符串（datetime-local 在提交前转换）。
 * `minReadSeconds` 留空即 `null`，由后端使用默认值；`supersedesId` 用于「更正走新建」（决策 D7）。
 */
export interface CreateAnnouncementPayload {
  title: string
  content: string
  level: AnnouncementLevel
  startAt: string
  endAt?: string | null
  minReadSeconds?: number | null
  requireScrollToEnd: boolean
  requireConfirm: boolean
  supersedesId?: number | null
}
