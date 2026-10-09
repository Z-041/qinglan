<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import type { ReviewTargetType } from '@shared/types/review-common'
import { availableReviewActions, blockedByOpenProposal } from '@/composables/reviewActionSet'
import type { ReviewAction } from '@/types/review'

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

/**
 * 可执行动作集合。判定逻辑下沉到 `reviewActionSet`，与队列页的快捷键绑定共用同一份——
 * 否则「按钮不给的动作，快捷键却能按」这类不一致迟早出现。
 */
const actions = computed(() =>
  availableReviewActions({
    targetType: props.targetType,
    targetStatus: props.targetStatus,
    canRevoke: props.canRevoke === true,
  }),
)
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
        :disabled="props.submitting || blockedByOpenProposal(action.key, props.hasOpenProposal === true)"
        :loading="props.submitting && pendingKey === action.key"
        @click="handleClick(action.key)"
      >
        {{ action.label }}
      </BaseButton>
    </div>
  </div>
</template>
