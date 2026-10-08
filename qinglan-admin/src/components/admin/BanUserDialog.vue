<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'

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
  <el-dialog
    :model-value="props.open"
    title="封禁用户"
    width="440px"
    @update:model-value="emit('close')"
  >
    <div class="flex flex-col gap-4">
      <FormField
        label="封禁天数"
        :error="daysValid ? '' : `请输入 ${MIN_DAYS} - ${MAX_DAYS} 之间的整数`"
      >
        <input v-model="days" type="number" :min="MIN_DAYS" :max="MAX_DAYS" class="ql-field" />
      </FormField>

      <FormField label="封禁理由" hint="会写入审计日志">
        <BaseTextarea v-model="reason" :rows="3" :maxlength="500" placeholder="说明封禁依据" />
      </FormField>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
          取消
        </BaseButton>
        <BaseButton
          variant="danger"
          size="sm"
          :loading="props.submitting"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          确认封禁
        </BaseButton>
      </div>
    </template>
  </el-dialog>
</template>
