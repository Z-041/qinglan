import { computed } from 'vue'

import { messageBoxState, useMessageBox } from './useMessageBox'

/**
 * 「填理由」二次确认弹窗的统一封装（底层是自研 {@link useMessageBox}，不再依赖 Element Plus）。
 *
 * 四处用法——审核队列的单人处置、权限变更、解封、驳回帖子——的取消按钮、输入占位与
 * 「非空」校验逐字相同，仅提示语（message）与标题不同；确认按钮除解封为「确认解封」外为「确认」。
 * 收进一处，避免某处悄悄放宽校验（理由要写进审计日志，空理由等于没有留痕）。
 *
 * 另外暴露 `promptOpen`：审核队列与待审帖子队列在弹窗打开期间不得让轮询替换列表。
 * 它从单例状态派生，无需各处自行维护开关。用户取消返回 `null`，调用方据此静默返回。
 */
export function useReasonPrompt() {
  const { promptReason } = useMessageBox()

  const promptOpen = computed(() => messageBoxState.request !== null)

  return { promptOpen, promptReason }
}
