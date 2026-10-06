import { httpGet, httpPost } from './http'

import type { AdminStats, AdminUserQuery, BanUserPayload, CreditAdjustPayload } from '@/types/admin'
import type { CursorPage } from '@/types/api'
import type { ReasonPayload } from '@/types/review'
import type { UserProfile } from '@/types/user'

/**
 * 只把非空字段交给 axios（null / undefined 会被 axios 序列化时忽略，见 http.ts）。
 * 顺序与后端 AdminUserQuery 的参数顺序保持一致，便于对照。
 */
function queryParams(query: AdminUserQuery): Record<string, unknown> {
  return {
    cursor: query.cursor,
    status: query.status,
    role: query.role,
    minCredit: query.minCredit,
    maxCredit: query.maxCredit,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
  }
}

/** 用户管理列表：游标分页 + 组合筛选。不支持身份搜索（spec 7.2） */
export function fetchAdminUsers(query: AdminUserQuery): Promise<CursorPage<UserProfile>> {
  return httpGet<CursorPage<UserProfile>>('/admin/users', queryParams(query))
}

/** 看板统计：全部指标一次返回 */
export function fetchAdminStats(): Promise<AdminStats> {
  return httpGet<AdminStats>('/admin/stats/overview')
}

/** 封禁 days 天；写审计（不变量 #7） */
export function banUser(userId: number, payload: BanUserPayload): Promise<void> {
  return httpPost<void>(`/admin/users/${userId}/ban`, payload)
}

export function unbanUser(userId: number, payload: ReasonPayload): Promise<void> {
  return httpPost<void>(`/admin/users/${userId}/unban`, payload)
}

export function adjustCredit(userId: number, payload: CreditAdjustPayload): Promise<void> {
  return httpPost<void>(`/admin/users/${userId}/credit`, payload)
}
