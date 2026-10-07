import { onScopeDispose, ref } from 'vue'

/** 验证码重发冷却秒数。后端另有频次限制，这里做体验层收敛，避免连点把同一邮箱刷爆 */
export const CODE_RESEND_COOLDOWN_SECONDS = 60

/**
 * 验证码重发倒计时。调用方在「发送成功」后 `start()`，据 `remaining` 禁用按钮并展示剩余秒数。
 *
 * 组件卸载时清理定时器（onScopeDispose），避免离开页面后仍每秒自减、徒留一个悬挂的 interval。
 */
export function useCodeCooldown(seconds: number = CODE_RESEND_COOLDOWN_SECONDS) {
  const remaining = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  function stop(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function start(): void {
    stop()
    remaining.value = seconds
    timer = setInterval(() => {
      remaining.value -= 1
      if (remaining.value <= 0) {
        stop()
      }
    }, 1000)
  }

  onScopeDispose(stop)

  return { remaining, start }
}
