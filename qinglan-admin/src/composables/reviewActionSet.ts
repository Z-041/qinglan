import type { ReviewTargetType } from '@shared/types/review-common'
import type { AdminPermission } from '@/types/permission'
import type { ReviewAction } from '@/types/review'

export interface ReviewActionMeta {
  key: ReviewAction
  label: string
  variant: 'primary' | 'ghost' | 'danger'
  /**
   * 一键处置快捷键。**每个动作在任何状态下都是同一个键**——
   * 若改成按当前动作集顺序编号，焦点从「待审帖」移到「已下架帖」后同一个键的含义就会漂移。
   */
  shortcut: string
  /** 该动作所需权限点（与后端端点注解一一对应）；无权限时不渲染也不响应 */
  permission: AdminPermission
}

/** 处置动作的唯一元数据来源：按钮文案、变体、快捷键、权限点都在此一处 */
export const REVIEW_ACTION_META: Record<ReviewAction, ReviewActionMeta> = {
  approve: { key: 'approve', label: '通过', variant: 'primary', shortcut: '1', permission: 'POST_REVIEW' },
  reject: { key: 'reject', label: '驳回', variant: 'ghost', shortcut: '2', permission: 'POST_REVIEW' },
  'take-down': { key: 'take-down', label: '下架', variant: 'danger', shortcut: '3', permission: 'PROPOSAL_CREATE' },
  dismiss: { key: 'dismiss', label: '举报不成立', variant: 'ghost', shortcut: '4', permission: 'REPORT_DISMISS' },
  uphold: { key: 'uphold', label: '维持下架', variant: 'ghost', shortcut: '5', permission: 'PROPOSAL_CREATE' },
  'hide-contact': {
    key: 'hide-contact',
    label: '隐藏联系方式',
    variant: 'ghost',
    shortcut: '6',
    permission: 'PROPOSAL_CREATE',
  },
  'remove-comment': {
    key: 'remove-comment',
    label: '移除评论',
    variant: 'danger',
    shortcut: '7',
    permission: 'PROPOSAL_CREATE',
  },
  restore: { key: 'restore', label: '恢复可见', variant: 'primary', shortcut: '8', permission: 'DISPOSITION_REVOKE' },
  'show-contact': {
    key: 'show-contact',
    label: '恢复联系方式',
    variant: 'ghost',
    shortcut: '9',
    permission: 'DISPOSITION_REVOKE',
  },
}

/** 需经委员会表决的动作：同目标已有进行中提案时必然被唯一键挡成 409 */
export const PROPOSAL_ACTIONS = new Set<ReviewAction>(['take-down', 'hide-contact', 'uphold', 'remove-comment'])

/**
 * 撤销已作出处置的动作：下架与隐藏联系方式都经委员会表决才成立，撤销权归超管
 * （{@code DISPOSITION_REVOKE}）。若任一委员可撤销，多数决就不构成约束。
 */
export const REVOKE_ACTIONS = new Set<ReviewAction>(['restore', 'show-contact'])

export interface ReviewActionSetInput {
  targetType: ReviewTargetType
  targetStatus: string
  /** 是否可撤销已作出处置；不传视为无权限（默认安全） */
  canRevoke: boolean
}

/**
 * 按目标类型 / 状态给出可执行动作（与 ReviewActionBar 的按钮一一对应）。
 * 评论只有「移除评论 / 举报不成立」；帖子按状态取动作，恢复类动作以 canRevoke 为门槛。
 */
export function availableReviewActions({
  targetType,
  targetStatus,
  canRevoke,
}: ReviewActionSetInput): ReviewActionMeta[] {
  const visible = (action: ReviewAction) => canRevoke || !REVOKE_ACTIONS.has(action)

  if (targetType === 'COMMENT') {
    return [REVIEW_ACTION_META['remove-comment'], REVIEW_ACTION_META.dismiss].filter((action) =>
      visible(action.key),
    )
  }

  const list: ReviewActionMeta[] = []
  if (targetStatus === 'PENDING' || targetStatus === 'REVIEWING') {
    list.push(REVIEW_ACTION_META.approve, REVIEW_ACTION_META.reject)
  }
  if (targetStatus === 'TAKEN_DOWN') {
    list.push(REVIEW_ACTION_META.restore, REVIEW_ACTION_META.uphold)
  } else {
    list.push(REVIEW_ACTION_META['take-down'])
  }
  list.push(REVIEW_ACTION_META['hide-contact'], REVIEW_ACTION_META['show-contact'], REVIEW_ACTION_META.dismiss)
  return list.filter((action) => visible(action.key))
}

/** 该动作是否会撞上进行中提案的唯一键（此时按钮禁用 / 快捷键不出现） */
export function blockedByOpenProposal(action: ReviewAction, hasOpenProposal: boolean): boolean {
  return hasOpenProposal && PROPOSAL_ACTIONS.has(action)
}
