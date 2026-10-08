import type { AnnouncementLevel } from '@shared/types/announcement'
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
