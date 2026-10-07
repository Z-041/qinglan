import { computed, ref } from 'vue'

import { fetchPendingAnnouncement, markAnnouncementRead } from '@/api/announcement'
import { ApiError, readStoredValue, writeStoredValue } from '@/api/http'
import type { PendingAnnouncement } from '@/types/announcement'

/** 游客设备标识在 localStorage 中的键名（计划 D4） */
export const DEVICE_ID_STORAGE_KEY = 'qinglan.device-id'

/** 标记已读连续失败达到该次数后放行关闭——不能因为后端一直失败就把用户永久困在弹窗里 */
const MAX_READ_ATTEMPTS = 3
/** 拉取待读公告失败后的一次重试间隔 */
const LOAD_RETRY_DELAY_MS = 2000
const MAX_LOAD_ATTEMPTS = 2

/**
 * 全局公告弹窗状态。**模块级单例**：供 `<AnnouncementModal>` 与 `main.ts` 的 428 回调共用
 * （与 useToast 同一手法——状态在模块作用域、导出 `useAnnouncement()` 取用）。
 */
const current = ref<PendingAnnouncement | null>(null)
const remainingSeconds = ref(0)
const scrolledToEnd = ref(false)
const confirmed = ref(false)
const submitting = ref(false)
/** 标记已读的连续失败次数；> 0 时界面要给出「重试 / 关闭」而不是把用户锁死 */
const readFailureCount = ref(0)

/** 弹窗展示时刻，用于计算真实停留时长（不变量 #6）——绝不等于「打开即已读」 */
let shownAt = 0
/** 倒计时目标时刻。按「目标时刻 − 现在」算，而不是每秒减一：标签页被浏览器挂起时
 *  setInterval 会被节流，减一写法会让界面显示还要等更久，与服务端收到的真实时长口径不一致 */
let deadlineAt = 0
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 存储不可用时的兜底：本次会话内保持同一个 id，避免每次调用都换一个（那会让已读记录永远对不上） */
let memoryDeviceId: string | null = null

/**
 * 读取（必要时生成并持久化）游客设备标识。
 *
 * 游客也要能被记录已读、跨刷新不重复弹（计划 D4），故必须落 localStorage；
 * 存储不可用（禁用站点数据）时退化为会话内稳定的内存值，功能降级但不至于每次弹同一条公告。
 */
export function deviceId(): string {
  const stored = readStoredValue(DEVICE_ID_STORAGE_KEY)
  if (stored) {
    memoryDeviceId = stored
    return stored
  }
  // 首次访问，或存储不可用（读不到）——后者要靠 memoryDeviceId 保持会话内稳定，
  // 否则每次调用都换一个 id，同一条公告会被反复当成「没读过」
  if (!memoryDeviceId) {
    memoryDeviceId = crypto.randomUUID()
  }
  writeStoredValue(DEVICE_ID_STORAGE_KEY, memoryDeviceId)
  return memoryDeviceId
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

/** 标记已读失败过——界面据此切出「重试 / 关闭」两个出口 */
const readFailed = computed(() => readFailureCount.value > 0)

function stopCountdown(): void {
  if (countdownTimer !== null) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function tickCountdown(): void {
  const left = Math.max(0, Math.ceil((deadlineAt - Date.now()) / 1000))
  remainingSeconds.value = left
  if (left === 0) {
    stopCountdown()
  }
}

function startCountdown(seconds: number): void {
  stopCountdown()
  deadlineAt = Date.now() + Math.max(0, Math.floor(seconds)) * 1000
  tickCountdown()
  if (remainingSeconds.value > 0) {
    countdownTimer = setInterval(tickCountdown, 1000)
  }
}

/** 复位全部状态。弹窗的显隐只由 `current` 决定（原 `visible` 与它恒等，已移除避免两份真相） */
function reset(): void {
  stopCountdown()
  current.value = null
  remainingSeconds.value = 0
  scrolledToEnd.value = false
  confirmed.value = false
  submitting.value = false
  readFailureCount.value = 0
}

/** 展示一条公告并开始倒计时；**一次只展示一条，绝不堆叠**（设计方案 5.5） */
function present(announcement: PendingAnnouncement): void {
  current.value = announcement
  scrolledToEnd.value = false
  confirmed.value = false
  submitting.value = false
  readFailureCount.value = 0
  shownAt = Date.now()
  startCountdown(announcement.minReadSeconds)
}

/**
 * 本次会话里已经放过行的公告 id。
 *
 * 标记已读失败后如果立刻重新拉取，会拿到**同一条**仍未读的公告、弹窗马上再次出现，
 * 等于换个方式把用户锁住。故本次会话跳过它；刷新页面后 `skippedIds` 清空、仍会再次要求阅读
 * （强制不落空），而发帖依旧受 428 拦截，用户并没有因此获得任何额外能力。
 */
const skippedIds = new Set<number>()

/**
 * 清空「本次会话已放行」记录。**不要在 `reset()` 里做这件事**——`loadNext()` 内部也会调
 * `reset()`，那样会把刚记下的跳过项立刻抹掉，弹窗随后又回来。
 * 只在身份切换（登录/退出）与测试复位时调用：换了身份应当重新评估待读公告。
 */
export function clearSkippedAnnouncements(): void {
  skippedIds.clear()
}

/**
 * 拉取下一条未读公告：有则弹出，无则收摊。
 *
 * 失败会**重试一次**（等 LOAD_RETRY_DELAY_MS）；仍失败则静默——错误已由 http 拦截器提示，
 * 等下一次身份或路由变化再拉，避免在弹窗里堆第二次提示。
 */
async function loadNext(attempt = 1): Promise<void> {
  try {
    const pending = await fetchPendingAnnouncement(deviceId())
    if (pending && !skippedIds.has(pending.id)) {
      present(pending)
    } else {
      reset()
    }
  } catch {
    if (attempt < MAX_LOAD_ATTEMPTS) {
      await new Promise((resolve) => setTimeout(resolve, LOAD_RETRY_DELAY_MS))
      await loadNext(attempt + 1)
    }
  }
}

/**
 * 拉取入口：应用启动、登录态变化、进入学生端页面都调它。
 *
 * **正在展示的公告不会被覆盖、倒计时也不会被重置**——否则用户读到一半切个页面就得重读。
 */
export async function refreshPending(): Promise<void> {
  if (current.value) {
    return
  }
  await loadNext()
}

/** 428 分支专用：把接口带回的公告快照直接开成弹窗，不再多发一次请求（计划 D15） */
export function openFromPrecondition(announcement: PendingAnnouncement): void {
  // 已在展示就不覆盖：428 带回的通常就是当前这条，覆盖会把已读时长清零、倒计时重来
  if (current.value) {
    return
  }
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

type MarkReadOutcome = 'not-ready' | 'ok' | 'fatal' | 'retryable'

/**
 * 标记已读——**不变量 #6 的落点**。
 *
 * 仅在 `canClose` 为真（时长满、必要时已滚到底、必要时已勾选）时才调用；
 * `durationMs` 取「弹窗展示至今的真实毫秒数」，**绝不在打开弹窗时即标记已读**。
 *
 * 失败要分类：公告已被撤回/删除（404/410）重试永远不会成功，直接计入「可放行」；
 * 其余（网络、5xx、429）计一次失败但仍可重试。
 */
async function markRead(): Promise<MarkReadOutcome> {
  const announcement = current.value
  if (!announcement || !canClose.value || submitting.value) {
    return 'not-ready'
  }
  submitting.value = true
  try {
    await markAnnouncementRead(announcement.id, {
      deviceId: deviceId(),
      durationMs: Date.now() - shownAt,
      scrolledToEnd: scrolledToEnd.value,
      confirmed: confirmed.value,
    })
    readFailureCount.value = 0
    return 'ok'
  } catch (error) {
    const status = error instanceof ApiError ? error.status : undefined
    if (status === 404 || status === 410) {
      readFailureCount.value = MAX_READ_ATTEMPTS
      return 'fatal'
    }
    readFailureCount.value += 1
    return 'retryable'
  } finally {
    submitting.value = false
  }
}

/** 把当前这条记为「本次会话不再打扰」，避免失败后拉回来又是同一条 */
function skipCurrent(): void {
  if (current.value) {
    skippedIds.add(current.value.id)
  }
}

/**
 * 关闭当前弹窗并继续取下一条。
 *
 * 三条放行路径，确保**任何情况下都不会把用户锁在弹窗里**：
 * 标记成功；标记遇到的失败不可能靠重试解决（公告已撤回）；或已连续失败到上限。
 * 其余情况留在弹窗，界面会切出「重试 / 关闭」——注意真强制在 428，放行关闭不会让用户获得
 * 任何额外能力（发帖依旧会被拦），所以这里优先保证「有出口」。
 */
async function closeAndContinue(): Promise<void> {
  if (!current.value) {
    return
  }
  if (!canClose.value) {
    // 强制阅读的三个条件还没满足：保持弹窗（原有行为，不因失败处理放松）
    return
  }
  const outcome = await markRead()
  if (outcome === 'ok') {
    reset()
    await loadNext()
    return
  }
  if (outcome === 'fatal' || readFailureCount.value >= MAX_READ_ATTEMPTS) {
    skipCurrent()
    reset()
    await loadNext()
  }
}

/** 显式关闭：仅在标记已读**已经失败过**时才允许（界面只在 readFailed 时暴露该按钮） */
async function forceClose(): Promise<void> {
  if (readFailureCount.value === 0) {
    return
  }
  skipCurrent()
  reset()
  await loadNext()
}

export function useAnnouncement() {
  return {
    current,
    remainingSeconds,
    scrolledToEnd,
    confirmed,
    submitting,
    canClose,
    readFailed,
    loadNext,
    refreshPending,
    openFromPrecondition,
    noteScrolledToEnd,
    confirmUnderstood,
    markRead,
    closeAndContinue,
    forceClose,
    reset,
    clearSkippedAnnouncements,
    deviceId,
  }
}
