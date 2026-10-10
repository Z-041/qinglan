import { getCurrentScope, onMounted, onScopeDispose } from 'vue'

/**
 * 可见性感知的定时轮询原语：只在页面可见时按间隔执行 {@link run}，切后台即暂停、切回恢复；
 * 带防重入（上一轮未返回时不再叠加请求）。组件内使用时自动随生命周期启停，单测在无 scope
 * 环境调用则手动 `start` / `stop`。
 *
 * 抽出来是因为 useQueuePolling 与 useTodoCounts 各自复制了一整套「计时器 + 可见性 + 防重入 +
 * 生命周期」脚手架，两者仅「探针逻辑」与「start 时是否先立即跑一次」不同。
 *
 * @param options.immediate start / 切回可见时是否立即执行一次（默认 false，等第一个间隔）
 */
export function useVisibilityPolling(
  run: () => void | Promise<void>,
  options: { intervalMs: number; immediate?: boolean },
) {
  const { intervalMs, immediate = false } = options

  let timer: ReturnType<typeof setInterval> | null = null
  /** 防重入：上一轮未返回时不再叠加 */
  let inFlight = false

  function isVisible(): boolean {
    return typeof document === 'undefined' || document.visibilityState === 'visible'
  }

  /** 单次执行：可见且不在途才跑；失败静默（错误由 http 拦截器统一负责，下一轮自会重试） */
  async function tick(): Promise<void> {
    if (!isVisible() || inFlight) {
      return
    }
    inFlight = true
    try {
      await run()
    } catch {
      // 静默：轮询失败不该打断页面
    } finally {
      inFlight = false
    }
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

  function handleVisibilityChange(): void {
    if (isVisible()) {
      if (immediate) {
        void tick()
      }
      startTimer()
    } else {
      stopTimer()
    }
  }

  function start(): void {
    document.addEventListener('visibilitychange', handleVisibilityChange)
    if (isVisible()) {
      if (immediate) {
        void tick()
      }
      startTimer()
    }
  }

  function stop(): void {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    stopTimer()
  }

  if (getCurrentScope()) {
    onMounted(start)
    onScopeDispose(stop)
  }

  return { tick, start, stop }
}
