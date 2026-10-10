<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'
import ReasonDialogShell from './ReasonDialogShell.vue'

const props = defineProps<{ open: boolean; submitting?: boolean }>()

const emit = defineEmits<{ submit: [reason: string]; close: [] }>()

const reason = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      reason.value = ''
    }
  },
)

const canSubmit = computed(() => reason.value.trim().length > 0)

function handleSubmit(): void {
  if (!canSubmit.value) {
    return
  }
  emit('submit', reason.value.trim())
}
</script>

<template>
  <ReasonDialogShell
    :open="props.open"
    title="举报不成立"
    confirm-text="确认驳回举报"
    :submitting="props.submitting"
    :confirm-disabled="!canSubmit"
    @close="emit('close')"
    @confirm="handleSubmit"
  >
    <FormField label="处理说明" hint="会写入审计日志，供后续追溯">
      <BaseTextarea v-model="reason" :rows="3" :maxlength="500" placeholder="说明为何认定举报不成立" />
    </FormField>
  </ReasonDialogShell>
</template>
