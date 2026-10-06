<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import FormField from '@/components/base/FormField.vue'

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
  <el-dialog
    :model-value="props.open"
    title="举报不成立"
    width="480px"
    @update:model-value="emit('close')"
  >
    <FormField label="处理说明" hint="会写入审计日志，供后续追溯">
      <BaseTextarea v-model="reason" :rows="3" :maxlength="500" placeholder="说明为何认定举报不成立" />
    </FormField>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">取消</BaseButton>
        <BaseButton size="sm" :loading="props.submitting" :disabled="!canSubmit" @click="handleSubmit">
          确认驳回举报
        </BaseButton>
      </div>
    </template>
  </el-dialog>
</template>
