<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import FormField from '@shared/components/base/FormField.vue'
import type { ArbitratePayload, ArbitrationDecision } from '@/types/proposal'
import DialogShell from './DialogShell.vue'

/**
 * 超管裁决弹窗（仅僵局提案）。
 *
 * 裁决推翻委员会表决，**必须填理由**，否则无法追溯「为什么这么判」——
 * 前端先做非空校验，不把明显的 400 丢给用户（后端 @NotBlank 仍是真正的防线）。
 * 有两个提交目标（准予 / 不予），故关闭 Shell 的 Enter 确认并自定义 footer。
 */

/** 与后端 ArbitrateRequest 的 @Size(max = 200) 对齐 */
const MAX_REASON_LENGTH = 200

const props = defineProps<{ open: boolean; submitting?: boolean }>()

const emit = defineEmits<{ submit: [payload: ArbitratePayload]; close: [] }>()

const reason = ref('')
const showError = ref(false)

// 每次打开都回到初始态，避免上一轮的理由残留到下一次裁决
watch(
  () => props.open,
  (open) => {
    if (open) {
      reason.value = ''
      showError.value = false
    }
  },
)

const canSubmit = computed(() => reason.value.trim().length > 0)

function handleSubmit(decision: ArbitrationDecision): void {
  if (!canSubmit.value) {
    showError.value = true
    return
  }
  emit('submit', { decision, reason: reason.value.trim() })
}
</script>

<template>
  <DialogShell
    :open="props.open"
    title="裁决提案"
    :enter-to-confirm="false"
    @close="emit('close')"
  >
    <FormField
      label="裁决理由"
      :error="showError ? '请填写裁决理由' : undefined"
      hint="必填，会写入提案结论与审计日志"
    >
      <el-input
        v-model="reason"
        type="textarea"
        :class="{ 'ql-field-error': showError && !canSubmit }"
        :rows="3"
        :maxlength="MAX_REASON_LENGTH"
        placeholder="说明裁决依据"
      />
    </FormField>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
          取消
        </BaseButton>
        <BaseButton
          variant="ghost"
          size="sm"
          :loading="props.submitting"
          :disabled="props.submitting"
          @click="handleSubmit('DENY')"
        >
          不予处置
        </BaseButton>
        <BaseButton
          variant="danger"
          size="sm"
          :loading="props.submitting"
          :disabled="props.submitting"
          @click="handleSubmit('GRANT')"
        >
          准予处置
        </BaseButton>
      </div>
    </template>
  </DialogShell>
</template>
