import { ref } from 'vue'

import { bindStudentId, sendEmailCode, verifyEmail } from '@/api/auth'
import { fetchCurrentUser } from '@/api/user'
import { pushToast } from '@shared/composables/useToast'
import { useUserStore } from '@/stores/user'

/**
 * 认证流程编排：邮箱验证码 → 校验 → （新用户）绑定学号。
 * 视图只消费本 composable，不直接调用 api。
 */
export function useAuth() {
  const userStore = useUserStore()

  const sending = ref(false)
  const verifying = ref(false)
  const binding = ref(false)
  const devCode = ref<string | null>(null)
  const needsStudentBinding = ref(false)

  /** 短时邮箱凭证，仅保存在内存，不落 localStorage */
  let emailToken: string | null = null

  async function requestCode(email: string) {
    sending.value = true
    try {
      const result = await sendEmailCode({ email })
      devCode.value = result.devCode
      return result
    } finally {
      sending.value = false
    }
  }

  async function verify(email: string, code: string) {
    verifying.value = true
    try {
      const result = await verifyEmail({ email, code })
      if (result.needsStudentBinding) {
        emailToken = result.emailToken
        needsStudentBinding.value = true
        return { needsStudentBinding: true as const }
      }
      userStore.setAccessToken(result.accessToken)
      userStore.setProfile(result.user)
      return { needsStudentBinding: false as const }
    } finally {
      verifying.value = false
    }
  }

  async function bind(studentId: string) {
    if (!emailToken) {
      // 本地校验失败不经过 http 拦截器，这里主动走全局弹窗，避免静默失败
      pushToast('邮箱认证凭证已失效，请重新获取验证码', 'error')
      throw new Error('邮箱认证凭证缺失，请重新获取验证码')
    }
    binding.value = true
    try {
      const result = await bindStudentId({ emailToken, studentId })
      userStore.setAccessToken(result.accessToken)
      userStore.setProfile(result.user)
      emailToken = null
      needsStudentBinding.value = false
      return result
    } finally {
      binding.value = false
    }
  }

  async function loadProfile() {
    const profile = await fetchCurrentUser()
    userStore.setProfile(profile)
    return profile
  }

  return {
    sending,
    verifying,
    binding,
    devCode,
    needsStudentBinding,
    requestCode,
    verify,
    bind,
    loadProfile,
  }
}
