<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'
import DialogShell from './DialogShell.vue'

const props = defineProps<{ open: boolean; submitting?: boolean }>()

const emit = defineEmits<{ submit: [delta: number, reason: string]; close: [] }>()

/** 与后端 user/dto/CreditAdjustRequest 的 @Min(-100) @Max(100) 对齐 */
const MIN_DELTA = -100
const MAX_DELTA = 100

const delta = ref('')
const reason = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      delta.value = ''
      reason.value = ''
    }
  },
)

const deltaValue = computed(() => Number(delta.value))
const deltaValid = computed(
  () =>
    delta.value.trim().length > 0 &&
    Number.isInteger(deltaValue.value) &&
    deltaValue.value !== 0 &&
    deltaValue.value >= MIN_DELTA &&
    deltaValue.value <= MAX_DELTA,
)
const canSubmit = computed(() => deltaValid.value && reason.value.trim().length > 0)

/**
 * type="number" 上的 v-model 会把值转成 number，破坏「状态字段恒为 string」这一前提
 * （deltaValid 里的 .trim() 会抛 TypeError，按钮永久禁用）。改用 :value + @input
 * 把值收敛回字符串，逻辑与常量一律不动。
 */
function onDeltaInput(event: Event): void {
  delta.value = (event.target as HTMLInputElement).value
}

function handleSubmit(): void {
  if (!canSubmit.value) {
    return
  }
  emit('submit', deltaValue.value, reason.value.trim())
}
</script>

<template>
  <DialogShell
    :open="props.open"
    title="调整信用分"
    width="440px"
    confirm-text="确认调整"
    :submitting="props.submitting"
    :confirm-disabled="!canSubmit"
    @close="emit('close')"
    @confirm="handleSubmit"
  >
    <div class="flex flex-col gap-4">
      <FormField
        label="分值变化"
        :hint="`正数加分、负数扣分，单次范围 ${MIN_DELTA} ~ ${MAX_DELTA}`"
        :error="deltaValid || !delta.trim() ? '' : '请输入范围内非 0 的整数'"
      >
        <input
          :value="delta"
          type="number"
          :min="MIN_DELTA"
          :max="MAX_DELTA"
          class="ql-field"
          :class="!deltaValid && delta.trim() !== '' ? '!border-danger' : ''"
          :aria-invalid="!deltaValid && delta.trim() !== ''"
          @input="onDeltaInput"
        />
      </FormField>

      <FormField label="调整理由" hint="会写入审计日志">
        <BaseTextarea v-model="reason" :rows="3" :maxlength="500" placeholder="说明调整依据" />
      </FormField>
    </div>
  </DialogShell>
</template>
