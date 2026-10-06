import axios, { type AxiosError } from 'axios'

import { pushToast } from '@/composables/useToast'
import { SUCCESS_CODE, type ApiResponse } from '@/types/api'

/** 访问令牌在 localStorage 中的键名 */
export const TOKEN_STORAGE_KEY = 'qinglan.access-token'

/** 收到 401 时的处理回调，由应用入口注入 */
type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/**
 * 注册 401 处理器：由 main.ts 注入「重置用户态 + 回认证页」。
 *
 * 拦截器刻意不直接 import store / router——store 反向依赖本模块的 TOKEN_STORAGE_KEY，
 * 直接引用会形成循环依赖（AGENTS.md 第七章）。
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

/**
 * 承载后端错误码的异常，便于调用方按 code 分支处理。
 *
 * 约定：所有请求错误都已由拦截器统一 toast 提示，调用方**不要再内联渲染错误文案**，
 * 只需据此结束 loading 态、决定是否推进流程即可（见 AGENTS.md 第七章）。
 */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 10000,
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const code = error.response?.data?.code ?? 'QL-500-001'
    const message = error.response?.data?.message ?? '网络异常，请稍后重试'

    if (status === 401) {
      // 令牌已失效：先清持久化，再交给应用层重置内存态（Pinia 里的令牌与档案）并跳认证页
      localStorage.removeItem(TOKEN_STORAGE_KEY)
      unauthorizedHandler?.()
    }

    pushToast(message, 'error')

    return Promise.reject(new ApiError(code, message, status))
  },
)

async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await request
  const body = response.data
  if (body.code !== SUCCESS_CODE) {
    pushToast(body.message, 'error')
    throw new ApiError(body.code, body.message, 200)
  }
  return body.data
}

/** 仅 src/api 内部使用：所有 HTTP 请求都必须经由此处 */
export function httpGet<T>(url: string): Promise<T> {
  return unwrap(instance.get<ApiResponse<T>>(url))
}

export function httpPost<T>(url: string, body?: unknown): Promise<T> {
  return unwrap(instance.post<ApiResponse<T>>(url, body))
}

export function httpDelete<T>(url: string): Promise<T> {
  return unwrap(instance.delete<ApiResponse<T>>(url))
}

/**
 * multipart 上传。不手动设 Content-Type，交给浏览器补上含 boundary 的头部，
 * 手写会导致 boundary 与请求体不匹配、后端解析失败。
 */
export function httpPostForm<T>(url: string, form: FormData): Promise<T> {
  return unwrap(instance.post<ApiResponse<T>>(url, form))
}

export default instance
