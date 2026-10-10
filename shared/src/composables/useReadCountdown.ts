import { computed, ref, toValue, type MaybeRefOrGetter } from 'vue'

/**
 * 强制阅读门控的**纯逻辑**：deadline 式倒计时 + 三条件 AND（时长满 / 需滚动时已到底 /
 * 需确认时已勾选）。
 *
 * 学生端强读弹窗（useAnnouncement）与管理端「发布前学生视角预览」共用这一份，
 * 业务尾巴各自保留：学生端负责 markRead / 失败重试 / 428，预览端不落库。
 *
 * 倒计时按「目标时刻 − 现在」计算而非每秒减一：标签页被浏览器挂起时 setInterval 会被
 * 节流，减一写法会让界面显示与真实停留时长脱节。
 *
 * 不注册 onBeforeUnmount——本 composable 可能在模块级单例（非 setup）中使用；
 * 需要随组件卸载停表的调用方自行 onBeforeUnmount(stop)。
 */
export interface ReadGateOptions {
  requireScrollToEnd: MaybeRefOrGetter<boolean>
  requireConfirm: MaybeRefOrGetter<boolean>
}

export function useReadCountdown(options: ReadGateOptions) {
  const remainingSeconds = ref(0)
  const scrolledToEnd = ref(false)
  const confirmed = ref(false)

  let deadlineAt = 0
  let timer: ReturnType<typeof setInterval> | null = null

  /** 三条件 AND；倒计时未走完时其余两项无意义 */
  const canClose = computed(() => {
    if (remainingSeconds.value > 0) {
      return false
    }
    if (toValue(options.requireScrollToEnd) && !scrolledToEnd.value) {
      return false
    }
    if (toValue(options.requireConfirm) && !confirmed.value) {
      return false
    }
    return true
  })

  function tick(): void {
    const left = Math.max(0, Math.ceil((deadlineAt - Date.now()) / 1000))
    remainingSeconds.value = left
    if (left === 0) {
      stop()
    }
  }

  function stop(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  /** 开始一轮新的阅读：重置交互信号并启动倒计时（seconds 为 0 时立即满足时长条件） */
  function start(seconds: number): void {
    stop()
    scrolledToEnd.value = false
    confirmed.value = false
    deadlineAt = Date.now() + Math.max(0, Math.floor(seconds)) * 1000
    tick()
    if (remainingSeconds.value > 0) {
      timer = setInterval(tick, 1000)
    }
  }

  /** 全部归零并停表（弹窗关闭 / 切公告时调用） */
  function reset(): void {
    stop()
    remainingSeconds.value = 0
    scrolledToEnd.value = false
    confirmed.value = false
  }

  function noteScrolledToEnd(): void {
    scrolledToEnd.value = true
  }

  function setConfirmed(value: boolean): void {
    confirmed.value = value
  }

  return {
    remainingSeconds,
    scrolledToEnd,
    confirmed,
    canClose,
    start,
    stop,
    reset,
    noteScrolledToEnd,
    setConfirmed,
  }
}
