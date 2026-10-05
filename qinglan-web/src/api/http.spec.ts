import { AxiosError, type AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { useToast } from '@/composables/useToast'

import instance, { httpPost } from './http'

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
  })

  afterEach(() => {
    instance.defaults.adapter = originalAdapter
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
})
