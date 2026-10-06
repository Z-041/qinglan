<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import FormField from '@/components/base/FormField.vue'

const props = defineProps<{ open: boolean; submitting?: boolean }>()

const emit = defineEmits<{ submit: [approved: boolean, reason: string]; close: [] }>()

/** true = 通过申诉（内容恢复可见），false = 维持原判 */
const approved = ref(true)
const reason = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      approved.value = true
      reason.value = ''
    }
  },
)

const canSubmit = computed(() => reason.value.trim().length > 0)

function handleSubmit(): void {
  if (!canSubmit.value) {
    return
  }
  emit('submit', approved.value, reason.value.trim())
}
</script>

<template>
  <el-dialog
    :model-value="props.open"
    title="申诉复审"
    width="480px"
    @update:model-value="emit('close')"
  >
    <div class="flex flex-col gap-4">
      <FormField label="复审结论">
        <div class="flex gap-2">
          <label class="cursor-pointer">
            <input v-model="approved" class="peer sr-only" type="radio" name="appeal-verdict" :value="true" />
            <span
              class="block rounded-lg border border-line px-3 py-2 text-xs text-muted transition peer-checked:(border-primary bg-primary-soft font-medium text-primary-deep)"
            >
              通过申诉（恢复内容）
            </span>
          </label>
          <label class="cursor-pointer">
            <input v-model="approved" class="peer sr-only" type="radio" name="appeal-verdict" :value="false" />
            <span
              class="block rounded-lg border border-line px-3 py-2 text-xs text-muted transition peer-checked:(border-primary bg-primary-soft font-medium text-primary-deep)"
            >
              维持原判
            </span>
          </label>
        </div>
      </FormField>

      <FormField label="复审说明" hint="会写入审计日志">
        <BaseTextarea v-model="reason" :rows="3" :maxlength="500" placeholder="说明复审依据" />
      </FormField>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">取消</BaseButton>
        <BaseButton size="sm" :loading="props.submitting" :disabled="!canSubmit" @click="handleSubmit">
          提交复审
        </BaseButton>
      </div>
    </template>
  </el-dialog>
</template>
