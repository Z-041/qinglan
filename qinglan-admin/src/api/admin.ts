import { httpGet, httpGetBlob, httpPost } from '@shared/api/http'

import type { BlobDownload } from '@shared/api/http'
import type {
  AdminAnnouncementItem,
  AdminAuditLogItem,
  AdminMe,
  AdminStats,
  AdminUserQuery,
  AuditLogQuery,
  BanUserPayload,
  ChangeUserRolePayload,
  CreateAnnouncementPayload,
  CreditAdjustPayload,
} from '@/types/admin'
import type { CursorPage } from '@shared/types/api'
import type { ReasonPayload } from '@/types/review'
import type { UserProfile } from '@shared/types/user'

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

/** 公告列表：游标分页，cursor 为空表示第一页 */
export function fetchAdminAnnouncements(
  cursor: string | null = null,
): Promise<CursorPage<AdminAnnouncementItem>> {
  return httpGet<CursorPage<AdminAnnouncementItem>>('/admin/announcements', { cursor })
}

/** 新建公告草稿（ADMIN|SUPER_ADMIN 均可） */
export function createAnnouncement(payload: CreateAnnouncementPayload): Promise<AdminAnnouncementItem> {
  return httpPost<AdminAnnouncementItem>('/admin/announcements', payload)
}

/** 发布公告（仅 SUPER_ADMIN）；面向全员的强制弹窗入口，写审计（不变量 #7） */
export function publishAnnouncement(id: number): Promise<void> {
  return httpPost<void>(`/admin/announcements/${id}/publish`)
}

/** 撤回公告（仅 SUPER_ADMIN）；撤回后不可再发布（决策 D7） */
export function revokeAnnouncement(id: number): Promise<void> {
  return httpPost<void>(`/admin/announcements/${id}/revoke`)
}

// ------------------------------------------------------------------ P5 权限模型

/**
 * 当前管理端身份：角色 + 权限点（P5 7.2）。
 * 只要求登录，不要求权限点——它回答的正是「我有哪些权限点」。
 */
export function fetchAdminMe(): Promise<AdminMe> {
  return httpGet<AdminMe>('/admin/me')
}

/**
 * 委员会成员（管理员 + 评议员）。
 * 后端按 6 人上限约束，是有界集合，因此不分页。
 */
export function fetchCommittee(): Promise<UserProfile[]> {
  return httpGet<UserProfile[]>('/admin/users/committee')
}

/** 下放 / 收回 / 互转角色（仅超管；只接受 ADMIN / REVIEWER / STUDENT） */
export function changeUserRole(userId: number, payload: ChangeUserRolePayload): Promise<void> {
  return httpPost<void>(`/admin/users/${userId}/role`, payload)
}

/** 审计日志：游标分页 + 可选动作 / 时间筛选（仅超管） */
export function fetchAuditLogs(query: AuditLogQuery = {}): Promise<CursorPage<AdminAuditLogItem>> {
  return httpGet<CursorPage<AdminAuditLogItem>>('/admin/audit-logs', {
    cursor: query.cursor,
    action: query.action,
    from: query.from,
    to: query.to,
  })
}

/** 审计日志导出（阶段 B）：按当前筛选条件流式下载 CSV；导出本身写审计（不变量 #7） */
export function exportAuditLogs(query: AuditLogQuery = {}): Promise<BlobDownload> {
  return httpGetBlob('/admin/audit-logs/export', {
    action: query.action,
    from: query.from,
    to: query.to,
  })
}

/**
 * 用户列表导出（阶段 B）：按当前查询条件下载 CSV。
 * 导出的是**当前筛选的完整结果集**（无游标），故直接复用列表的规范化后的查询参数。
 */
export function exportAdminUsers(query: AdminUserQuery): Promise<BlobDownload> {
  return httpGetBlob('/admin/users/export', queryParams(query))
}
