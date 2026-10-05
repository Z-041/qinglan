import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { TOKEN_STORAGE_KEY } from '@/api/http'
import type { UserProfile } from '@/types/user'

/**
 * 用户态：仅保存访问令牌与当前用户档案。
 * 按 AGENTS.md 前端分层，store 不承载任何业务规则（业务逻辑放 composables）。
 */
export const useUserStore = defineStore('user', () => {
  const accessToken = ref<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY))
  const profile = ref<UserProfile | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value))
  const isAdmin = computed(
    () => profile.value?.role === 'ADMIN' || profile.value?.role === 'SUPER_ADMIN',
  )

  function setAccessToken(token: string | null): void {
    accessToken.value = token
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token)
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
  }

  function setProfile(value: UserProfile | null): void {
    profile.value = value
  }

  function signOut(): void {
    setAccessToken(null)
    setProfile(null)
  }

  return { accessToken, profile, isAuthenticated, isAdmin, setAccessToken, setProfile, signOut }
})
