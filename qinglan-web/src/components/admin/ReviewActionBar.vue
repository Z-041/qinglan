<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import type { ReviewAction, ReviewTargetType } from '@/types/review'

interface ActionMeta {
  key: ReviewAction
  label: string
  variant: 'primary' | 'ghost' | 'danger'
}

const props = defineProps<{
  targetType: ReviewTargetType
  targetStatus: string
  submitting?: boolean
}>()

const emit = defineEmits<{ action: [action: ReviewAction] }>()

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
 * 联系方式的隐藏与恢复都是幂等操作，两态常驻，保证验收标准 4 的两条路径都能走通。
 */
const actions = computed<ActionMeta[]>(() => {
  if (props.targetType === 'COMMENT') {
    return [ACTION_META['remove-comment'], ACTION_META.dismiss]
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
  return list
})
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <BaseButton
      v-for="action in actions"
      :key="action.key"
      :variant="action.variant"
      size="sm"
      :disabled="props.submitting"
      :loading="props.submitting && pendingKey === action.key"
      @click="handleClick(action.key)"
    >
      {{ action.label }}
    </BaseButton>
  </div>
</template>
