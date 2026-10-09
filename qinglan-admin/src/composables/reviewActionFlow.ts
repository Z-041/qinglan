import type { ReviewAction } from '@/types/review'

/**
 * 动作的输入形态：
 * - `direct` 直接执行，无需输入（通过）
 * - `reason` 需填自由文本理由（ElMessageBox 二次确认）
 * - `dialog` 走专用弹窗收集载荷（提案弹窗 / 驳回举报弹窗）
 */
export type ActionInput = 'direct' | 'reason' | 'dialog'

/**
 * 处置动作的**输入类别**——视图据它数据驱动地分发，不再写 switch。
 *
 * <p>与 {@link REVIEW_ACTION_META}（文案 / 权限）配对：本表只回答「这个动作需要什么输入」，
 * 「调哪个执行器」由视图持有的执行器映射决定（执行器是 `useReviewActions()` 绑定的函数，
 * 无法放进纯表）。两张 `Record<ReviewAction, …>` 都是穷尽的，新增动作时 TS 会强制补全，不会静默漂移。</p>
 */
export const REVIEW_ACTION_FLOW: Record<ReviewAction, ActionInput> = {
  approve: 'direct',
  reject: 'reason',
  restore: 'reason',
  'show-contact': 'reason',
  dismiss: 'dialog',
  'take-down': 'dialog',
  'hide-contact': 'dialog',
  uphold: 'dialog',
  'remove-comment': 'dialog',
}
