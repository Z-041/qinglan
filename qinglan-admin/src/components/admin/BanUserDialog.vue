<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import FormField from '@shared/components/base/FormField.vue'
import DialogShell from './DialogShell.vue'

const props = defineProps<{ open: boolean; submitting?: boolean }>()

const emit = defineEmits<{ submit: [days: number, reason: string]; close: [] }>()

/** 与后端 user/dto/BanUserRequest 的 @Min(1) @Max(3650) 对齐 */
const MIN_DAYS = 1
const MAX_DAYS = 3650

const days = ref(String(MIN_DAYS))
const reason = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      days.value = String(MIN_DAYS)
      reason.value = ''
    }
  },
)

const daysValue = computed(() => Number(days.value))
const daysValid = computed(
  () =>
    Number.isInteger(daysValue.value) &&
    daysValue.value >= MIN_DAYS &&
    daysValue.value <= MAX_DAYS,
)
const canSubmit = computed(() => daysValid.value && reason.value.trim().length > 0)

function handleSubmit(): void {
  if (!canSubmit.value) {
    return
  }
  emit('submit', daysValue.value, reason.value.trim())
}
</script>

<template>
  <DialogShell
    :open="props.open"
    title="封禁用户"
    :enter-to-confirm="false"
    confirm-text="确认封禁"
    confirm-variant="danger"
    :submitting="props.submitting"
    :confirm-disabled="!canSubmit"
    @close="emit('close')"
    @confirm="handleSubmit"
  >
    <div class="flex flex-col gap-4">
      <FormField
        label="封禁天数"
        :error="daysValid ? '' : `请输入 ${MIN_DAYS} - ${MAX_DAYS} 之间的整数`"
      >
        <el-input v-model="days" :class="{ 'ql-field-error': !daysValid }" inputmode="numeric" />
      </FormField>

      <FormField label="封禁理由" hint="会写入审计日志">
        <el-input v-model="reason" type="textarea" :rows="3" :maxlength="500" placeholder="说明封禁依据" />
      </FormField>
    </div>
  </DialogShell>
</template>
