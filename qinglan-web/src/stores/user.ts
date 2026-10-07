import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { TOKEN_STORAGE_KEY, readStoredValue, removeStoredValue, writeStoredValue } from '@/api/http'
import type { UserProfile } from '@/types/user'

/**
 * 用户态：仅保存访问令牌与当前用户档案。
 * 按 AGENTS.md 前端分层，store 不承载任何业务规则（业务逻辑放 composables）。
 */
export const useUserStore = defineStore('user', () => {
  // 走安全读写：本行在应用挂载前执行，裸访问 localStorage 在禁用站点数据的浏览器里会白屏
  const accessToken = ref<string | null>(readStoredValue(TOKEN_STORAGE_KEY))
  const profile = ref<UserProfile | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function setAccessToken(token: string | null): void {
    accessToken.value = token
    if (token) {
      writeStoredValue(TOKEN_STORAGE_KEY, token)
    } else {
      removeStoredValue(TOKEN_STORAGE_KEY)
    }
  }

  function setProfile(value: UserProfile | null): void {
    profile.value = value
  }

  function signOut(): void {
    setAccessToken(null)
    setProfile(null)
  }

  return { accessToken, profile, isAuthenticated, setAccessToken, setProfile, signOut }
})
