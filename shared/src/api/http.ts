import axios, { type AxiosError } from 'axios'

import { pushToast } from '@shared/composables/useToast'
import { SUCCESS_CODE, type ApiResponse } from '@shared/types/api'

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

/** 收到 428（强制阅读前置条件未满足）时的处理回调，由应用入口注入 */
type PreconditionRequiredHandler = (data: unknown) => void

let preconditionRequiredHandler: PreconditionRequiredHandler | null = null

/**
 * 注册 428 处理器：由 main.ts 注入「把携带的公告快照开成弹窗」。
 *
 * 与 401 同一手法，拦截器不直接 import composable，避免循环依赖（AGENTS.md 第七章）。
 */
export function setPreconditionRequiredHandler(handler: PreconditionRequiredHandler | null): void {
  preconditionRequiredHandler = handler
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

/**
 * 读 Blob 文本。优先用 `Blob.text()`；老环境（含单测用的 jsdom）没有它，
 * 退回 `FileReader`，避免下载错误体的解析在这些环境下静默失效。
 */
async function readBlobText(blob: Blob): Promise<string> {
  if (typeof blob.text === 'function') {
    return blob.text()
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error ?? new Error('读取响应体失败'))
    reader.readAsText(blob)
  })
}

/**
 * 下载接口的响应体是二进制（{@link httpGetBlob} 用 `responseType: 'blob'`），
 * 出错时后端返回的却是 JSON。axios 会把这个 JSON 原样包成 Blob，于是拦截器读不到
 * `code` / `message`，只能退化成通用「网络异常」——导出超限这类**可行动的原因**就丢了。
 * 这里把 Blob 读回文本再解析；读不出 JSON（例如真拿到了 CSV）就返回 null。
 */
async function readApiResponseFromBlob(blob: Blob): Promise<ApiResponse<unknown> | null> {
  try {
    const text = await readBlobText(blob)
    return text ? (JSON.parse(text) as ApiResponse<unknown>) : null
  } catch {
    return null
  }
}

/** 从 `Content-Disposition` 里取文件名（`attachment; filename="xxx.csv"`）；拿不到返回 null */
export function parseFileName(disposition: string | undefined | null): string | null {
  if (!disposition) {
    return null
  }
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  return match ? decodeURIComponent(match[1]) : null
}

/** 二进制下载的返回：文件内容 + 后端给出的文件名（可能缺失） */
export interface BlobDownload {
  blob: Blob
  fileName: string | null
}

/**
 * localStorage 的安全读写。
 *
 * 浏览器禁用站点数据（或部分隐私模式）时，`localStorage` 的读写会抛 `SecurityError`。
 * 而这里的调用点都在应用启动路径上——store 初始化读令牌、每个请求的拦截器读令牌——
 * 裸调用会让整个应用挂不起来（白屏）。存储不可用时降级为「读到 null、写入忽略」，
 * 表现为「未登录 + 登录态只在内存里」，而不是打不开。
 *
 * 放在本模块是因为它就近守着 `TOKEN_STORAGE_KEY`；前端目录结构由 AGENTS.md 第一章枚举，
 * 不为此新建 utils/ 目录。
 */
export function readStoredValue(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeStoredValue(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 存储不可用：静默降级为仅内存态
  }
}

export function removeStoredValue(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // 同上
  }
}

/**
 * 内存态令牌，是请求头的**权威来源**。
 *
 * 为什么不能只读 localStorage：存储不可用时 `writeStoredValue` 会静默失败，而 store 里的内存态
 * 仍然被置上——于是「内存说已登录、请求却不带 Authorization」，每个请求都 401 → 被登出 → 再登录
 * 还是同样结果，用户表现为「登录不进去」。以内存为准即可让两者始终一致，存储只负责跨刷新的持久化。
 */
let memoryToken: string | null = null

/**
 * 是否已经处理过一次 401。
 *
 * 页面首屏常并发多个请求，令牌失效时它们会**同时**返回 401。没有这道闸，每个 401 都会
 * 弹一条 toast 并调一次 unauthorizedHandler（跳一次登录页），用户会看到提示闪成一片、
 * 路由被反复 replace。重新拿到令牌（setAuthToken 传入非空值）后解除抑制。
 */
let unauthorizedHandled = false

export function setAuthToken(token: string | null): void {
  memoryToken = token
  if (token) {
    // 重新登录：下一次令牌失效仍需处理
    unauthorizedHandled = false
    writeStoredValue(TOKEN_STORAGE_KEY, token)
  } else {
    removeStoredValue(TOKEN_STORAGE_KEY)
  }
}

export function getAuthToken(): string | null {
  return memoryToken ?? readStoredValue(TOKEN_STORAGE_KEY)
}

instance.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    // 错误体可能是 Blob（下载接口）：先读回文本解析出 code / message，别把明确原因退化成「网络异常」
    let raw: unknown = error.response?.data
    if (raw instanceof Blob) {
      raw = await readApiResponseFromBlob(raw)
    }
    const body = (raw ?? null) as ApiResponse<unknown> | null
    const code = body?.code ?? 'QL-500-001'
    const message = body?.message ?? '网络异常，请稍后重试'

    if (status === 401) {
      // 令牌已失效：内存态与持久化一并清掉，再交给应用层重置 Pinia 并跳认证页
      memoryToken = null
      removeStoredValue(TOKEN_STORAGE_KEY)
      // 并发 401 只处理一次：否则 N 个请求同时失效会弹 N 条提示、跳 N 次登录页
      if (!unauthorizedHandled) {
        unauthorizedHandled = true
        pushToast(message, 'error')
        unauthorizedHandler?.()
      }
      throw new ApiError(code, message, status)
    }

    if (status === 428) {
      // 强制阅读：把响应体携带的公告快照交给公告弹窗直接渲染，且**不再 toast**——
      // 弹窗本身就是提示，再 toast 会出现「弹窗 + 提示」两遍（AGENTS.md 第七章）。
      //
      // 但必须先确认「确实有人接」：管理端不发帖、因而**不注册**该处理器（428 由发帖触发），
      // 若直接 `handler?.(...)` 就会既无弹窗也无提示、用户以为点了没反应。
      // 故未注册时退化为 toast——把「管理端不会遇到 428」这一假设变成代码保证。
      if (preconditionRequiredHandler) {
        preconditionRequiredHandler(body?.data)
      } else {
        pushToast(message, 'error')
      }
    } else {
      pushToast(message, 'error')
    }

    throw new ApiError(code, message, status)
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

/**
 * 仅 src/api 内部使用：所有 HTTP 请求都必须经由此处。
 * params 里的 null / undefined 由 axios 序列化时自动忽略，调用方不必先过滤。
 */
export function httpGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return unwrap(instance.get<ApiResponse<T>>(url, { params }))
}

export function httpPost<T>(url: string, body?: unknown): Promise<T> {
  return unwrap(instance.post<ApiResponse<T>>(url, body))
}

/**
 * 二进制下载（CSV 导出）。与 JSON 客户端分开的原因：
 * 成功响应是文件流（不是 `ApiResponse`），且失败时后端返回的是 JSON——错误体经 axios 包成 Blob 后
 * 由响应拦截器解析回 `code` / `message`（见上文），调用方只需按 {@link ApiError} 处理。
 *
 * 若后端误把错误按 `200 + application/json` 返回，这里也兜住：解析成 `ApiResponse` 并抛错，
 * 绝不把一段 JSON 当成 CSV 落盘（“下载到的文件内容是一段错误 JSON”比报错更难排查）。
 */
export async function httpGetBlob(
  url: string,
  params?: Record<string, unknown>,
): Promise<BlobDownload> {
  const response = await instance.get<Blob>(url, { params, responseType: 'blob' })
  const contentType = String(response.headers['content-type'] ?? '')
  if (contentType.includes('application/json')) {
    const body = await readApiResponseFromBlob(response.data)
    const code = body?.code ?? 'QL-500-001'
    const message = body?.message ?? '导出失败，请稍后重试'
    pushToast(message, 'error')
    throw new ApiError(code, message, response.status)
  }
  return {
    blob: response.data,
    fileName: parseFileName(String(response.headers['content-disposition'] ?? '')),
  }
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
