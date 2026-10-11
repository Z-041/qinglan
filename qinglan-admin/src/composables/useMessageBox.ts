import { reactive } from 'vue'

export interface MessageBoxRequest {
  kind: 'confirm' | 'prompt'
  title: string
  message: string
  confirmText: string
  cancelText: string
  /** prompt 专用：输入框占位 */
  placeholder: string
  /** 确认按钮是否用危险色（封禁 / 删除等不可逆动作） */
  danger: boolean
  /** prompt 专用校验：返回错误文案表示不通过，返回 null 表示通过 */
  validate: (value: string) => string | null
}

interface MessageBoxState {
  request: MessageBoxRequest | null
  /** prompt 的当前输入 */
  input: string
  /** 校验错误文案（空串表示无错） */
  error: string
}

/**
 * 命令式确认 / 输入弹窗的**模块级单例状态**，替代 ElMessageBox。
 *
 * 为什么是模块级单例：确认框由 composable / 事件处理器调用（不在模板里），只能走「调用 → 等
 * Promise」的命令式形态；同一时刻只允许一个（与 ElMessageBox 的单例语义一致，也避免遮罩叠加）。
 *
 * 与 ElMessageBox 的两点差异：① 取消 / 关闭都解析为 `null`（布尔确认则解析为 false），**不抛异常**
 * ——调用方判空即可，不必 try/catch；② `useMessageBox()` 每次调用返回同一份状态，故它更像
 * 「命令集合」而非「独立实例」。
 */
export const messageBoxState = reactive<MessageBoxState>({ request: null, input: '', error: '' })

let pendingResolve: ((result: string | null) => void) | null = null

function open(request: MessageBoxRequest): Promise<string | null> {
  messageBoxState.request = request
  messageBoxState.input = ''
  messageBoxState.error = ''
  return new Promise((resolve) => {
    pendingResolve = resolve
  })
}

/** 由 MessageBoxHost 调用：accepted=true 提交（prompt 取当前输入），false 视为取消。 */
export function resolveMessageBox(accepted: boolean): void {
  const resolve = pendingResolve
  const result = accepted ? messageBoxState.input : null
  pendingResolve = null
  messageBoxState.request = null
  messageBoxState.input = ''
  messageBoxState.error = ''
  resolve?.(result)
}

export function useMessageBox() {
  /** 二次确认：确认返回 true，取消 / 关闭返回 false（不抛） */
  async function confirm(message: string, title: string, confirmText = '确认'): Promise<boolean> {
    const result = await open({
      kind: 'confirm',
      title,
      message,
      confirmText,
      cancelText: '取消',
      placeholder: '',
      danger: false,
      validate: () => null,
    })
    return result !== null
  }

  /**
   * 填理由：确认返回去空格后的理由，取消 / 关闭返回 null（不抛）。
   * 统一的「非空」校验写进 validate——理由要进审计日志，空理由等于没有留痕。
   */
  async function promptReason(title: string, message: string, confirmText = '确认'): Promise<string | null> {
    const result = await open({
      kind: 'prompt',
      title,
      message,
      confirmText,
      cancelText: '取消',
      placeholder: '理由',
      danger: false,
      validate: (value) => (value.trim().length > 0 ? null : '理由不能为空'),
    })
    return result === null ? null : result.trim()
  }

  return { confirm, promptReason }
}
