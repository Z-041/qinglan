<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseInput from '@shared/components/base/BaseInput.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { ReportReason } from '@shared/types/review-common'
import type { TakeDownPayload } from '@/types/review'

/** 与后端 TakeDownPostRequest 的 @Max(3650) 对齐 */
const MAX_BAN_DAYS = 3650

/** 展示顺序同举报理由：高频在前，OTHER 兜底 */
const REASONS: ReportReason[] = [
  'ADVERTISEMENT',
  'PORNOGRAPHY',
  'ABUSE',
  'PRIVACY',
  'FRAUD',
  'ILLEGAL',
  'IRRELEVANT',
  'OTHER',
]

const props = defineProps<{ open: boolean; submitting?: boolean }>()

const emit = defineEmits<{ submit: [payload: TakeDownPayload]; close: [] }>()

const { reportReasonLabel } = useUserLabels()

const reasonCode = ref<ReportReason>('ABUSE')
const detail = ref('')
const banDaysInput = ref('')

// 每次打开都回到初始态，避免上一轮的选择残留到下一次下架
watch(
  () => props.open,
  (open) => {
    if (open) {
      reasonCode.value = 'ABUSE'
      detail.value = ''
      banDaysInput.value = ''
    }
  },
)

const banDays = computed<number | null>(() => {
  const raw = banDaysInput.value.trim()
  if (raw === '') {
    return null
  }
  const value = Number(raw)
  return Number.isInteger(value) && value >= 1 && value <= MAX_BAN_DAYS ? value : null
})

/** 填了但不是合法天数：禁用提交，避免把非法值当「不封禁」静默丢掉 */
const banDaysInvalid = computed(() => banDaysInput.value.trim() !== '' && banDays.value === null)

function handleSubmit(): void {
  if (banDaysInvalid.value) {
    return
  }
  emit('submit', {
    reasonCode: reasonCode.value,
    detail: detail.value.trim() || null,
    banDays: banDays.value,
  })
}
</script>

<template>
  <el-dialog
    :model-value="props.open"
    title="下架帖子"
    width="480px"
    @update:model-value="emit('close')"
  >
    <div class="flex flex-col gap-4">
      <FormField label="违规类型">
        <select v-model="reasonCode" class="ql-field">
          <option v-for="reason in REASONS" :key="reason" :value="reason">
            {{ reportReasonLabel(reason) }}
          </option>
        </select>
      </FormField>

      <FormField label="补充说明" hint="可选，会写入下架理由展示给作者">
        <BaseTextarea v-model="detail" :rows="3" :maxlength="400" placeholder="说明下架依据" />
      </FormField>

      <FormField
        label="并封禁（天）"
        :error="banDaysInvalid ? `请填 1-${MAX_BAN_DAYS} 的整数，留空表示只下架不封禁` : undefined"
      >
        <BaseInput v-model="banDaysInput" inputmode="numeric" placeholder="留空表示只下架不封禁" />
      </FormField>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">取消</BaseButton>
        <BaseButton
          variant="danger"
          size="sm"
          :loading="props.submitting"
          :disabled="banDaysInvalid"
          @click="handleSubmit"
        >
          确认下架
        </BaseButton>
      </div>
    </template>
  </el-dialog>
</template>
