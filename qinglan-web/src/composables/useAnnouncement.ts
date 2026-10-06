import { computed, ref } from 'vue'

import { fetchPendingAnnouncement, markAnnouncementRead } from '@/api/announcement'
import type { PendingAnnouncement } from '@/types/announcement'

/** 游客设备标识在 localStorage 中的键名（计划 D4） */
export const DEVICE_ID_STORAGE_KEY = 'qinglan.device-id'

/**
 * 全局公告弹窗状态。**模块级单例**：供 `<AnnouncementModal>` 与 `main.ts` 的 428 回调共用
 * （与 useToast 同一手法——状态在模块作用域、导出 `useAnnouncement()` 取用）。
 */
const current = ref<PendingAnnouncement | null>(null)
const visible = ref(false)
const remainingSeconds = ref(0)
const scrolledToEnd = ref(false)
const confirmed = ref(false)
const submitting = ref(false)

/** 弹窗展示时刻，用于计算真实停留时长（不变量 #6）——绝不等于「打开即已读」 */
let shownAt = 0
let countdownTimer: ReturnType<typeof setInterval> | null = null

/**
 * 读取（必要时生成并持久化）游客设备标识。
 *
 * 游客也要能被记录已读、跨刷新不重复弹（计划 D4），故必须落 localStorage。
 */
export function deviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_STORAGE_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(DEVICE_ID_STORAGE_KEY, id)
  }
  return id
}

/**
 * 三个强制条件（设计方案 5.2 的 AND）：倒计时满、必要时已滚到底、必要时已勾选。
 * 关闭按钮与 `markRead()` 都由它把关。
 */
const canClose = computed(() => {
  const announcement = current.value
  if (!announcement) {
    return false
  }
  if (remainingSeconds.value > 0) {
    return false
  }
  if (announcement.requireScrollToEnd && !scrolledToEnd.value) {
    return false
  }
  if (announcement.requireConfirm && !confirmed.value) {
    return false
  }
  return true
})

function stopCountdown(): void {
  if (countdownTimer !== null) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function startCountdown(seconds: number): void {
  stopCountdown()
  remainingSeconds.value = Math.max(0, Math.floor(seconds))
  if (remainingSeconds.value === 0) {
    return
  }
  countdownTimer = setInterval(() => {
    remainingSeconds.value = Math.max(0, remainingSeconds.value - 1)
    if (remainingSeconds.value === 0) {
      stopCountdown()
    }
  }, 1000)
}

/** 展示一条公告并开始倒计时；**一次只展示一条，绝不堆叠**（设计方案 5.5） */
function present(announcement: PendingAnnouncement): void {
  current.value = announcement
  visible.value = true
  scrolledToEnd.value = false
  confirmed.value = false
  submitting.value = false
  shownAt = Date.now()
  startCountdown(announcement.minReadSeconds)
}

/** 收摊并复位全部状态 */
function dismiss(): void {
  stopCountdown()
  current.value = null
  visible.value = false
  remainingSeconds.value = 0
  scrolledToEnd.value = false
  confirmed.value = false
}

/**
 * 拉取下一条未读公告：有则弹出，无则收摊。
 * 失败静默（错误已由 http 拦截器统一 toast），不改变当前展示。
 */
async function loadNext(): Promise<void> {
  try {
    const pending = await fetchPendingAnnouncement(deviceId())
    if (pending) {
      present(pending)
    } else {
      dismiss()
    }
  } catch {
    // 错误已由拦截器提示，此处静默避免二次提示
  }
}

/** 应用启动入口：登录态与游客态都要拉取（游客也会被强制阅读） */
export async function start(): Promise<void> {
  await loadNext()
}

/** 428 分支专用：把接口带回的公告快照直接开成弹窗，不再多发一次请求（计划 D15） */
export function openFromPrecondition(announcement: PendingAnnouncement): void {
  present(announcement)
}

/** 弹窗在正文滚动到底时调用 */
function noteScrolledToEnd(): void {
  scrolledToEnd.value = true
}

/** 勾选「我已了解」 */
function confirmUnderstood(): void {
  confirmed.value = true
}

/**
 * 标记已读——**不变量 #6 的落点**。
 *
 * 仅在 `canClose` 为真（时长满、必要时已滚到底、必要时已勾选）时才调用一次；
 * `durationMs` 取「弹窗展示至今的真实毫秒数」，**绝不在打开弹窗时即标记已读**。
 */
async function markRead(): Promise<boolean> {
  const announcement = current.value
  if (!announcement || !canClose.value || submitting.value) {
    return false
  }
  submitting.value = true
  try {
    await markAnnouncementRead(announcement.id, {
      deviceId: deviceId(),
      durationMs: Date.now() - shownAt,
      scrolledToEnd: scrolledToEnd.value,
      confirmed: confirmed.value,
    })
    return true
  } catch {
    // 错误已由拦截器提示；保留弹窗供用户重试
    return false
  } finally {
    submitting.value = false
  }
}

/** 关闭当前弹窗（标记已读成功后）并继续取下一条 */
async function closeAndContinue(): Promise<void> {
  const marked = await markRead()
  if (!marked) {
    return
  }
  dismiss()
  await loadNext()
}

export function useAnnouncement() {
  return {
    current,
    visible,
    remainingSeconds,
    scrolledToEnd,
    confirmed,
    submitting,
    canClose,
    loadNext,
    start,
    openFromPrecondition,
    noteScrolledToEnd,
    confirmUnderstood,
    markRead,
    closeAndContinue,
    deviceId,
  }
}
