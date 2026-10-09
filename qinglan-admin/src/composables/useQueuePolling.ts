import { getCurrentScope, onMounted, onScopeDispose, ref } from 'vue'

/** 队列轮询间隔（方案：30 秒、可配）。低频 + 只取首屏做比对，避免给后端加压 */
export const QUEUE_POLL_INTERVAL_MS = 30_000

export interface QueuePollingOptions {
  /** 首屏探针：拉当前首屏并只回行键，用于与展示列表比对（不动列表状态） */
  fetchHeadKeys: () => Promise<string[]>
  /** 当前展示列表的行键（首屏是其前缀） */
  getDisplayedKeys: () => string[]
  /** 是否处置进行中（弹窗打开 / 提交中）：为真时不替换列表，只把角标挂起 */
  isBusy: () => boolean
  /** 合并刷新（复用队列自身的 refresh，保留游标与请求序号防竞态） */
  onRefresh: () => void | Promise<void>
  /** 轮询间隔，默认 {@link QUEUE_POLL_INTERVAL_MS} */
  intervalMs?: number
}

/** 首屏键数组是否是展示列表的前缀（长度不足一律视为「有新内容」） */
export function isPrefixEqual(head: readonly string[], displayed: readonly string[]): boolean {
  if (head.length > displayed.length) {
    return false
  }
  return head.every((key, index) => key === displayed[index])
}

/**
 * 队列自动刷新与新内容角标（方案「自动刷新与新内容角标」）。
 *
 * <p>仅在页面可见（`document.visibilityState === 'visible'`）时轮询，切后台即暂停、切回自动恢复。</p>
 *
 * <p><b>不打断处置</b>：发现新内容时先点亮角标；若此刻 {@link QueuePollingOptions.isBusy} 为真
 * （弹窗打开或提交中），只挂角标、**不替换列表**，等用户点击角标或处置结束后再合并刷新。</p>
 */
export function useQueuePolling(options: QueuePollingOptions) {
  const intervalMs = options.intervalMs ?? QUEUE_POLL_INTERVAL_MS

  /** 是否检测到列表有变化；点击角标（applyNow）后清除 */
  const hasNewContent = ref(false)

  let timer: ReturnType<typeof setInterval> | null = null
  /** 防重入：上一次探针未返回时不再叠加请求 */
  let inFlight = false

  function isVisible(): boolean {
    return typeof document === 'undefined' || document.visibilityState === 'visible'
  }

  function startTimer(): void {
    if (timer !== null) {
      return
    }
    timer = setInterval(() => {
      void tick()
    }, intervalMs)
  }

  function stopTimer(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  async function tick(): Promise<void> {
    // 切后台立刻停表；这里再兜一次，覆盖「定时器与切换同时发生」的窗口
    if (!isVisible() || inFlight) {
      return
    }
    inFlight = true
    try {
      const head = await options.fetchHeadKeys()
      if (isPrefixEqual(head, options.getDisplayedKeys())) {
        return
      }
      hasNewContent.value = true
      // 处置进行中：只把角标挂起，绝不替换列表，否则会抽走正在处置的目标
      if (options.isBusy()) {
        return
      }
      await options.onRefresh()
      // 刷新后仍不一致（如刷新失败）就保留角标，下一轮会再试
      hasNewContent.value = !isPrefixEqual(head, options.getDisplayedKeys())
    } catch {
      // 轮询失败静默：错误提示由 http 拦截器统一负责，下一轮自会重试
    } finally {
      inFlight = false
    }
  }

  /** 用户点击「有新内容」角标：合并刷新并清除角标 */
  async function applyNow(): Promise<void> {
    await options.onRefresh()
    hasNewContent.value = false
  }

  /**
   * 主动清除角标（不刷新）。筛选条件变化时会走这里——
   * 换了筛选口径后，旧角标说的「有新内容」已经不对应当前列表，继续挂着只会误导。
   */
  function clear(): void {
    hasNewContent.value = false
  }

  function handleVisibilityChange(): void {
    if (isVisible()) {
      startTimer()
    } else {
      stopTimer()
    }
  }

  function start(): void {
    document.addEventListener('visibilitychange', handleVisibilityChange)
    if (isVisible()) {
      startTimer()
    }
  }

  function stop(): void {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    stopTimer()
  }

  // 组件内使用时自动随生命周期启停；单测里在无 scope 环境调用则手动 start/stop
  if (getCurrentScope()) {
    onMounted(start)
    onScopeDispose(stop)
  }

  return { hasNewContent, applyNow, clear, start, stop }
}
