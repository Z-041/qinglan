import { httpGet } from '@shared/api/http'

import type { UserProfile } from '@shared/types/user'

export function fetchCurrentUser(): Promise<UserProfile> {
  return httpGet<UserProfile>('/users/me')
}
