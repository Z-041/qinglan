import { ref } from 'vue'

/**
 * 「填理由」二次确认弹窗（ElMessageBox.prompt）的统一封装。
 *
 * 四处用法——审核队列的单人处置、权限变更、解封、驳回帖子——的取消按钮、输入占位与
 * 「非空」校验逐字相同，仅提示语（message）与标题不同；确认按钮除解封为「确认解封」外为「确认」。
 * 收进一处，避免某处悄悄放宽校验（理由要写进审计日志，空理由等于没有留痕）。
 *
 * 另外暴露 `promptOpen`：审核队列与待审帖子队列在弹窗打开期间不得让轮询替换列表，
 * 故把「弹窗是否打开」也一并交出去。用户取消（ElMessageBox 以 reject 表示）不是错误，
 * 返回 `null`，调用方据此静默返回。
 */
export function useReasonPrompt() {
  const promptOpen = ref(false)

  async function promptReason(title: string, message: string, confirmText = '确认'): Promise<string | null> {
    promptOpen.value = true
    try {
      const result = await ElMessageBox.prompt(message, title, {
        confirmButtonText: confirmText,
        cancelButtonText: '取消',
        inputPlaceholder: '理由',
        inputValidator: (value: string) => (value.trim().length > 0 ? true : '理由不能为空'),
      })
      return result.value.trim()
    } catch {
      return null
    } finally {
      promptOpen.value = false
    }
  }

  return { promptOpen, promptReason }
}
