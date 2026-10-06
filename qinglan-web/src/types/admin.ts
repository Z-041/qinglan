import type { UserProfile, UserRole, UserStatus } from './user'

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
