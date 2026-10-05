import { reactive } from 'vue'

export type ToastType = 'error' | 'info'

export interface Toast {
  id: number
  message: string
  type: ToastType
}

/** 提示自动消失时长：足够读一眼，又不至于长时间占住页面顶部 */
export const TOAST_DURATION_MS = 4000

/** 模块级单例状态：任何地方（含 http 拦截器）都能推送提示 */
const toasts = reactive<Toast[]>([])
let nextId = 1

export function pushToast(message: string, type: ToastType = 'info'): void {
  const toast: Toast = { id: nextId++, message, type }
  toasts.push(toast)
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
