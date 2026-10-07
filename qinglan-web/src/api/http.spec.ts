import { AxiosError, type AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useToast } from '@/composables/useToast'

import instance, {
  httpGet,
  httpPost,
  setAuthToken,
  setUnauthorizedHandler,
  TOKEN_STORAGE_KEY,
} from './http'

const { toasts } = useToast()

/** 模拟后端以非 2xx 返回业务错误（GlobalExceptionHandler 的实际行为） */
function stubHttpFailure(status: number, code: string, message: string): void {
  instance.defaults.adapter = async (config) => {
    const response = {
      status,
      statusText: 'Error',
      data: { code, message, data: null },
      headers: {},
      config,
    } as AxiosResponse
    throw new AxiosError(message, 'ERR_BAD_REQUEST', config, null, response)
  }
}

/** 模拟 HTTP 200 但业务码非成功 */
function stubBusinessFailure(code: string, message: string): void {
  instance.defaults.adapter = async (config) =>
    ({
      status: 200,
      statusText: 'OK',
      data: { code, message, data: null },
      headers: {},
      config,
    }) as AxiosResponse
}

describe('http 全局错误提示', () => {
  const originalAdapter = instance.defaults.adapter

  beforeEach(() => {
    toasts.splice(0)
    localStorage.clear()
  })

  afterEach(() => {
    instance.defaults.adapter = originalAdapter
    setUnauthorizedHandler(null)
  })

  it('非 2xx 错误由拦截器统一弹一次提示', async () => {
    stubHttpFailure(400, 'QL-400-001', '验证码不正确')

    await expect(httpPost('/auth/verify-email', {})).rejects.toThrow('验证码不正确')

    expect(toasts.map((toast) => toast.type)).toEqual(['error'])
    expect(toasts.map((toast) => toast.message)).toEqual(['验证码不正确'])
  })

  it('HTTP 200 但业务码非成功时同样统一弹提示', async () => {
    stubBusinessFailure('QL-409-001', '请勿重复举报')

    await expect(httpPost('/reports', {})).rejects.toThrow('请勿重复举报')

    expect(toasts.map((toast) => toast.message)).toEqual(['请勿重复举报'])
  })

  it('401 时清空持久化令牌并回调注册的处理器（用于重置内存态）', async () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'stale-token')
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    stubHttpFailure(401, 'QL-401-001', '请先完成认证')

    await expect(httpGet('/user/me')).rejects.toThrow('请先完成认证')

    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('并发 401 只处理一次：不重复弹提示、不重复跳转', async () => {
    setAuthToken('fresh-token') // 模拟刚登录，解除上一次 401 的抑制
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    stubHttpFailure(401, 'QL-401-001', '请先完成认证')

    await Promise.allSettled([httpGet('/user/me'), httpGet('/notifications')])

    expect(handler).toHaveBeenCalledTimes(1)
    expect(toasts.map((toast) => toast.message)).toEqual(['请先完成认证'])
  })
})

describe('httpGet 查询参数', () => {
  const originalAdapter = instance.defaults.adapter

  afterEach(() => {
    instance.defaults.adapter = originalAdapter
  })

  it('把 params 原样交给 axios（null / undefined 由 axios 忽略）', async () => {
    let captured: unknown
    instance.defaults.adapter = async (config) => {
      captured = config.params
      return {
        status: 200,
        statusText: 'OK',
        data: { code: 'QL-000-000', message: 'ok', data: null },
        headers: {},
        config,
      } as AxiosResponse
    }

    await httpGet('/admin/users', { status: 'BANNED', role: null })

    expect(captured).toEqual({ status: 'BANNED', role: null })
  })
})
