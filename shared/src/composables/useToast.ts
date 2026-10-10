import { reactive } from 'vue'

export type ToastType = 'error' | 'info'

export interface Toast {
  id: number
  message: string
  type: ToastType
}

/** 提示自动消失时长：足够读一眼，又不至于长时间占住页面顶部 */
export const TOAST_DURATION_MS = 4000

/** 同时挂出的提示上限：批量失败（如多请求并发 4xx）时挤掉最旧的，避免无限堆叠盖满屏幕 */
export const MAX_TOASTS = 5

/** 模块级单例状态：任何地方（含 http 拦截器）都能推送提示 */
const toasts = reactive<Toast[]>([])
let nextId = 1

export function pushToast(message: string, type: ToastType = 'info'): void {
  const toast: Toast = { id: nextId++, message, type }
  toasts.push(toast)
  while (toasts.length > MAX_TOASTS) {
    // 保留最新：挤掉最旧一条（其定时器稍后触发 dismissToast 时找不到 id，安全跳过）
    toasts.shift()
  }
  // 到时自动移除；若期间被手动关闭，dismissToast 会因找不到 id 而安全跳过
  setTimeout(() => dismissToast(toast.id), TOAST_DURATION_MS)
}

export function dismissToast(id: number): void {
  const index = toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) {
    toasts.splice(index, 1)
  }
}

export function useToast() {
  return { toasts, pushToast, dismissToast }
}
