/** 与后端 user/entity/UserRole 一致 */
export type UserRole = 'STUDENT' | 'REVIEWER' | 'ADMIN' | 'SUPER_ADMIN'

/** 与后端 user/entity/UserStatus 一致 */
export type UserStatus = 'ACTIVE' | 'BANNED' | 'BLACKLISTED'

/**
 * 与后端 user/dto/UserProfileResponse 一一对应。
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
