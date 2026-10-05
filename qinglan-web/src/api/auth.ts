import { httpPost } from './http'

import type {
  AuthTokenResult,
  BindStudentIdPayload,
  SendCodePayload,
  SendCodeResult,
  VerifyEmailPayload,
  VerifyEmailResult,
} from '@/types/auth'

export function sendEmailCode(payload: SendCodePayload): Promise<SendCodeResult> {
  return httpPost<SendCodeResult>('/auth/email/code', payload)
}

export function verifyEmail(payload: VerifyEmailPayload): Promise<VerifyEmailResult> {
  return httpPost<VerifyEmailResult>('/auth/verify-email', payload)
}

export function bindStudentId(payload: BindStudentIdPayload): Promise<AuthTokenResult> {
  return httpPost<AuthTokenResult>('/auth/bind-student-id', payload)
}
