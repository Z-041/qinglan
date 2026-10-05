import { httpGet } from './http'

import type { UserProfile } from '@/types/user'

export function fetchCurrentUser(): Promise<UserProfile> {
  return httpGet<UserProfile>('/users/me')
}
