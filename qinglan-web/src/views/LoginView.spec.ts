import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { sendEmailCode, verifyEmail } from '@shared/api/auth'
import { setAuthToken, TOKEN_STORAGE_KEY } from '@shared/api/http'
import { useToast } from '@shared/composables/useToast'
import { useUserStore } from '@shared/stores/user'
import LoginView from '@/views/LoginView.vue'
import type { UserProfile } from '@shared/types/user'

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

vi.mock('@shared/api/auth', () => ({
  sendEmailCode: vi.fn(),
  verifyEmail: vi.fn(),
  bindStudentId: vi.fn(),
}))

const mockedSendCode = vi.mocked(sendEmailCode)
const mockedVerify = vi.mocked(verifyEmail)
const { toasts } = useToast()

function profile(role: UserProfile['role']): UserProfile {
  return {
    id: 1,
    role,
    status: 'ACTIVE',
    creditScore: 100,
    bannedUntil: null,
    createdAt: '2026-10-01T00:00:00Z',
  }
}

/** 走到「验证码」步并提交，返回已挂载的组件 */
async function loginAs(role: UserProfile['role']): Promise<VueWrapper> {
  mockedSendCode.mockResolvedValue({ expiresInSeconds: 300, devCode: '123456' })
  mockedVerify.mockResolvedValue({
    accessToken: 'access-token',
    emailToken: null,
    needsStudentBinding: false,
    user: profile(role),
  })

  const wrapper = mount(LoginView)
  await wrapper.get('input[type="email"]').setValue('someone@qinglan.edu.cn')
  await wrapper.get('form').trigger('submit')
  await flushPromises()

  await wrapper.get('input').setValue('123456')
  await wrapper.get('form').trigger('submit')
  await flushPromises()

  return wrapper
}

describe('LoginView 登录成功后的身份提示', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    setAuthToken(null)
    toasts.splice(0)
    vi.clearAllMocks()
  })

  it('管理员账号从前台登录时提示当前身份，不静默当作学生', async () => {
    await loginAs('ADMIN')

    expect(toasts.map((toast) => toast.message)).toContain('当前为管理员账号，可访问管理后台')
  })

  it('普通学生登录不弹该提示', async () => {
    await loginAs('STUDENT')

    expect(toasts.map((toast) => toast.message)).not.toContain('当前为管理员账号，可访问管理后台')
  })

  it('登录成功后令牌同时落到内存态与持久化，两者同源', async () => {
    await loginAs('STUDENT')

    expect(useUserStore().isAuthenticated).toBe(true)
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('access-token')
  })
})
