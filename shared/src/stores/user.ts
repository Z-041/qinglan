import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { getAuthToken, setAuthToken } from '@shared/api/http'
import type { UserProfile } from '@shared/types/user'

/**
 * 用户态：仅保存访问令牌与当前用户档案。
 * 按 AGENTS.md 前端分层，store 不承载任何业务规则（业务逻辑放 composables）。
 */
export const useUserStore = defineStore('user', () => {
  // 走 http 的安全读写：本行在应用挂载前执行，裸访问 localStorage 在禁用站点数据的浏览器里会白屏
  const accessToken = ref<string | null>(getAuthToken())
  const profile = ref<UserProfile | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  /**
   * 内存态与请求头必须同源：请求头由 api/http 的 getAuthToken() 提供（内存优先、存储兜底），
   * 若这里只写内存，存储不可用时就出现「已登录但请求不带令牌」→ 全部 401 → 登录不进去。
   */
  function setAccessToken(token: string | null): void {
    accessToken.value = token
    setAuthToken(token)
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
