<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import type { ReviewTargetType } from '@shared/types/review-common'
import type { ReviewAction } from '@/types/review'

interface ActionMeta {
  key: ReviewAction
  label: string
  variant: 'primary' | 'ghost' | 'danger'
}

const props = defineProps<{
  targetType: ReviewTargetType
  targetStatus: string
  submitting?: boolean
  /**
   * 该目标已有进行中的处置提案。此时四个「发起提案」动作必然被后端的唯一键挡成 409，
   * 直接禁用并说明原因，比让人提交后再吃一个冲突提示更好（方案 9.2）。
   */
  hasOpenProposal?: boolean
  /**
   * 是否可撤销已作出的处置（{@code DISPOSITION_REVOKE}，仅超管）。
   *
   * <p>不传即视为无权限——**默认安全**：漏传时表现为「按钮不出现」，
   * 而不是「任何人都能点」。真正的防线仍是后端注解，这里只管体验（P5 设计 4.4）。</p>
   */
  canRevoke?: boolean
}>()

const emit = defineEmits<{ action: [action: ReviewAction] }>()

/**
 * 二期起走表决的动作（P5 决策 D5）。其余动作（通过 / 驳回 / 驳回举报）仍是单人即时生效，
 * 不受进行中提案影响。
 */
const PROPOSAL_ACTIONS = new Set<ReviewAction>([
  'take-down',
  'hide-contact',
  'uphold',
  'remove-comment',
])

/**
 * 撤销已作出处置的动作：下架与隐藏联系方式都经委员会表决才成立，撤销权归超管
 * （{@code DISPOSITION_REVOKE}）。若任一委员可撤销，多数决就不构成约束。
 */
const REVOKE_ACTIONS = new Set<ReviewAction>(['restore', 'show-contact'])

function blockedByProposal(action: ReviewAction): boolean {
  return props.hasOpenProposal === true && PROPOSAL_ACTIONS.has(action)
}

function visible(action: ReviewAction): boolean {
  return props.canRevoke === true || !REVOKE_ACTIONS.has(action)
}

/**
 * 本次点击触发的动作。
 * 提交中所有按钮都要禁用（防重复处置），但 loading 只该落在被点的那个上——
 * 否则点「通过」会让一整排按钮同时转圈，看不出在提交哪个。
 */
const pendingKey = ref<ReviewAction | null>(null)

function handleClick(action: ReviewAction): void {
  pendingKey.value = action
  emit('action', action)
}

watch(
  () => props.submitting,
  (value) => {
    if (!value) {
      pendingKey.value = null
    }
  },
)

const ACTION_META: Record<ReviewAction, ActionMeta> = {
  approve: { key: 'approve', label: '通过', variant: 'primary' },
  reject: { key: 'reject', label: '驳回', variant: 'ghost' },
  'take-down': { key: 'take-down', label: '下架', variant: 'danger' },
  restore: { key: 'restore', label: '恢复可见', variant: 'primary' },
  uphold: { key: 'uphold', label: '维持下架', variant: 'ghost' },
  'hide-contact': { key: 'hide-contact', label: '隐藏联系方式', variant: 'ghost' },
  'show-contact': { key: 'show-contact', label: '恢复联系方式', variant: 'ghost' },
  'remove-comment': { key: 'remove-comment', label: '移除评论', variant: 'danger' },
  dismiss: { key: 'dismiss', label: '举报不成立', variant: 'ghost' },
}

/**
 * 可执行动作集合。
 * 评论只有「移除评论 / 举报不成立」；帖子按状态给动作：审核中可 通过 / 驳回，
 * 非下架态可 下架，下架态可 恢复可见 / 维持下架。
 * 联系方式的隐藏是发起提案，恢复（撤销隐藏）归超管，故两者不再「两态常驻」——
 * 是否有「恢复可见 / 恢复联系方式」取决于 canRevoke（{@code DISPOSITION_REVOKE}）。
 */
const actions = computed<ActionMeta[]>(() => {
  if (props.targetType === 'COMMENT') {
    return [ACTION_META['remove-comment'], ACTION_META.dismiss].filter((action) => visible(action.key))
  }

  const list: ActionMeta[] = []
  if (props.targetStatus === 'PENDING' || props.targetStatus === 'REVIEWING') {
    list.push(ACTION_META.approve, ACTION_META.reject)
  }
  if (props.targetStatus === 'TAKEN_DOWN') {
    list.push(ACTION_META.restore, ACTION_META.uphold)
  } else {
    list.push(ACTION_META['take-down'])
  }
  list.push(ACTION_META['hide-contact'], ACTION_META['show-contact'], ACTION_META.dismiss)
  return list.filter((action) => visible(action.key))
})
</script>

<template>
  <div class="flex flex-col gap-2">
    <p v-if="props.hasOpenProposal" class="text-xs text-muted">
      该目标已有进行中的处置提案：需等表决结果，暂不能再发起。可到「我的提案与投票」查看并投票。
    </p>
    <!-- 撤销权归超管：说清为什么这里没有「恢复可见」，否则会被当成功能缺失 -->
    <p v-if="!props.canRevoke && props.targetStatus === 'TAKEN_DOWN'" class="text-xs text-muted">
      撤销处置（恢复可见）仅超级管理员可执行：下架由委员会表决作出，撤销权属更高一级。
    </p>
    <div class="flex flex-wrap gap-2">
      <BaseButton
        v-for="action in actions"
        :key="action.key"
        :variant="action.variant"
        size="sm"
        :disabled="props.submitting || blockedByProposal(action.key)"
        :loading="props.submitting && pendingKey === action.key"
        @click="handleClick(action.key)"
      >
        {{ action.label }}
      </BaseButton>
    </div>
  </div>
</template>
