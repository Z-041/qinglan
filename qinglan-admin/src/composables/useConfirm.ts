/**
 * 二次确认框（ElMessageBox.confirm）的统一封装：确认返回 true；取消 / 关闭返回 false（不抛）。
 *
 * <p>与 {@link import('./useReasonPrompt')} 同——依赖 Element Plus，把「取消以 reject 表示」这一
 * 易错细节收在一处，调用方只判布尔。共用一个 `type: 'warning'` 与「取消」文案口径。</p>
 */
export function useConfirm() {
  async function confirm(message: string, title: string, confirmText = '确认'): Promise<boolean> {
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: confirmText,
        cancelButtonText: '取消',
        type: 'warning',
      })
      return true
    } catch {
      // 用户取消 / 关闭：不是错误，返回 false
      return false
    }
  }

  return { confirm }
}
