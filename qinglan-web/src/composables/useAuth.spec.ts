import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { bindStudentId, sendEmailCode, verifyEmail } from '@/api/auth'
import { useAuth } from '@/composables/useAuth'
import { TOKEN_STORAGE_KEY } from '@/api/http'
import { useUserStore } from '@/stores/user'
import type { UserProfile } from '@/types/user'

vi.mock('@/api/auth', () => ({
  sendEmailCode: vi.fn(),
  verifyEmail: vi.fn(),
  bindStudentId: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  fetchCurrentUser: vi.fn(),
}))

const studentProfile: UserProfile = {
  id: 7,
  role: 'STUDENT',
  status: 'ACTIVE',
  creditScore: 100,
  bannedUntil: null,
  createdAt: '2026-10-05T00:00:00Z',
}

const mockedSendEmailCode = vi.mocked(sendEmailCode)
const mockedVerifyEmail = vi.mocked(verifyEmail)
const mockedBindStudentId = vi.mocked(bindStudentId)

describe('useAuth', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('获取验证码后暴露联调验证码，并复位 sending 状态', async () => {
    mockedSendEmailCode.mockResolvedValue({ expiresInSeconds: 300, devCode: '135790' })

    const auth = useAuth()
    await auth.requestCode('stu@qinglan.edu.cn')

    expect(mockedSendEmailCode).toHaveBeenCalledWith({ email: 'stu@qinglan.edu.cn' })
    expect(auth.devCode.value).toBe('135790')
    expect(auth.sending.value).toBe(false)
  })

  it('新用户校验验证码后进入绑定学号步骤，不写入令牌', async () => {
    mockedVerifyEmail.mockResolvedValue({
      accessToken: null,
      emailToken: 'email-token',
      needsStudentBinding: true,
      user: null,
    })

    const auth = useAuth()
    const store = useUserStore()
    const result = await auth.verify('stu@qinglan.edu.cn', '135790')

    expect(result.needsStudentBinding).toBe(true)
    expect(auth.needsStudentBinding.value).toBe(true)
    expect(store.accessToken).toBeNull()
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('老用户校验验证码后直接写入令牌与档案', async () => {
    mockedVerifyEmail.mockResolvedValue({
      accessToken: 'access-token',
      emailToken: null,
      needsStudentBinding: false,
      user: studentProfile,
    })

    const auth = useAuth()
    const store = useUserStore()
    const result = await auth.verify('stu@qinglan.edu.cn', '135790')

    expect(result.needsStudentBinding).toBe(false)
    expect(store.accessToken).toBe('access-token')
    expect(store.profile?.id).toBe(7)
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('access-token')
  })

  it('绑定学号成功后写入令牌与档案', async () => {
    mockedVerifyEmail.mockResolvedValue({
      accessToken: null,
      emailToken: 'email-token',
      needsStudentBinding: true,
      user: null,
    })
    mockedBindStudentId.mockResolvedValue({
      accessToken: 'bound-token',
      tokenType: 'Bearer',
      expiresInSeconds: 7200,
      user: studentProfile,
    })

    const auth = useAuth()
    const store = useUserStore()
    await auth.verify('stu@qinglan.edu.cn', '135790')
    await auth.bind('20230001')

    expect(mockedBindStudentId).toHaveBeenCalledWith({ emailToken: 'email-token', studentId: '20230001' })
    expect(store.accessToken).toBe('bound-token')
    expect(auth.needsStudentBinding.value).toBe(false)
  })

  it('缺少邮箱凭证时绑定学号直接失败', async () => {
    const auth = useAuth()
    await expect(auth.bind('20230001')).rejects.toThrow('邮箱认证凭证缺失')
    expect(mockedBindStudentId).not.toHaveBeenCalled()
  })
})
