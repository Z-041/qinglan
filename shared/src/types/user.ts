/** 与后端 user/entity/UserRole 一致 */
export type UserRole = 'STUDENT' | 'REVIEWER' | 'ADMIN' | 'SUPER_ADMIN'

/** 与后端 user/entity/UserStatus 一致 */
export type UserStatus = 'ACTIVE' | 'BANNED' | 'BLACKLISTED'

/** 与后端 user/dto/UserProfileResponse 一一对应。
 * 不变量 #3：此结构永不包含邮箱、学号（明文或密文）。
 */
export interface UserProfile {
  id: number
  role: UserRole
  status: UserStatus
  creditScore: number
  bannedUntil: string | null
  createdAt: string
}

/**
 * 管理后台的准入角色（spec D3：REVIEWER 本轮不放行）。
 *
 * 后端 `SecurityConfig` 与前端路由守卫必须用同一份口径，否则会出现「前端放行、后端 403」
 * 或反过来「前端拦下、后端其实允许」。此处即唯一来源：路由守卫、管理端登录页、学生端登录页
 * 的身份提示都引用它，不再各自写一份。
 */
export const ADMIN_ROLES: UserRole[] = ['ADMIN', 'SUPER_ADMIN']

export function isAdminRole(role: UserRole | null | undefined): boolean {
  return role != null && ADMIN_ROLES.includes(role)
}
