import type { UserProfile } from './user'

/** 与后端 auth/dto/SendCodeRequest 对应 */
export interface SendCodePayload {
  email: string
}

/** 与后端 auth/dto/SendCodeResponse 对应；devCode 仅本地联调返回 */
export interface SendCodeResult {
  expiresInSeconds: number
  devCode: string | null
}

/** 与后端 auth/dto/VerifyEmailRequest 对应 */
export interface VerifyEmailPayload {
  email: string
  code: string
}

/** 与后端 auth/dto/VerifyEmailResponse 对应 */
export interface VerifyEmailResult {
  accessToken: string | null
  emailToken: string | null
  needsStudentBinding: boolean
  user: UserProfile | null
}

/** 与后端 auth/dto/BindStudentIdRequest 对应 */
export interface BindStudentIdPayload {
  emailToken: string
  studentId: string
}

/** 与后端 auth/dto/AuthTokenResponse 对应 */
export interface AuthTokenResult {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: UserProfile
}
