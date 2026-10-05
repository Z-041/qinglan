import type { UserRole, UserStatus } from '@/types/user'

/** 后端以英文枚举下发角色，界面不得直接展示，统一在此转成中文 */
const ROLE_LABELS: Record<UserRole, string> = {
  STUDENT: '学生',
  REVIEWER: '审核员',
  ADMIN: '管理员',
  SUPER_ADMIN: '超级管理员',
}

/** 用户状态同上 */
const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: '正常',
  BANNED: '已封禁',
  BLACKLISTED: '已拉黑',
}

/** 用户相关枚举的中文标签，供各页面共用 */
export function useUserLabels() {
  function roleLabel(role: UserRole): string {
    return ROLE_LABELS[role]
  }

  function statusLabel(status: UserStatus): string {
    return STATUS_LABELS[status]
  }

  return { roleLabel, statusLabel }
}
