<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import FormField from '@/components/base/FormField.vue'

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

function handleSubmit(): void {
  if (!canSubmit.value) {
    return
  }
  emit('submit', deltaValue.value, reason.value.trim())
}
</script>

<template>
  <el-dialog
    :model-value="props.open"
    title="调整信用分"
    width="440px"
    @update:model-value="emit('close')"
  >
    <div class="flex flex-col gap-4">
      <FormField
        label="分值变化"
        :hint="`正数加分、负数扣分，单次范围 ${MIN_DELTA} ~ ${MAX_DELTA}`"
        :error="deltaValid || !delta.trim() ? '' : '请输入范围内非 0 的整数'"
      >
        <input v-model="delta" type="number" :min="MIN_DELTA" :max="MAX_DELTA" class="ql-field" />
      </FormField>

      <FormField label="调整理由" hint="会写入审计日志">
        <BaseTextarea v-model="reason" :rows="3" :maxlength="500" placeholder="说明调整依据" />
      </FormField>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
          取消
        </BaseButton>
        <BaseButton size="sm" :loading="props.submitting" :disabled="!canSubmit" @click="handleSubmit">
          确认调整
        </BaseButton>
      </div>
    </template>
  </el-dialog>
</template>
