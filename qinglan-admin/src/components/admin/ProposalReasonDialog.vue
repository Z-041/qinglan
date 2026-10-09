<script setup lang="ts">
import { ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { ReportReason } from '@shared/types/review-common'
import type { ProposalDispositionPayload } from '@/types/review'

/**
 * 通用「发起处置提案」弹窗：下架 / 移除评论 / 隐藏联系方式 / 维持下架四处复用。
 * 只收违规类型与补充说明——动作由调用方决定（请求 URL 隐含），因此标题与确认文案由 props 传入。
 */

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

const props = defineProps<{
  open: boolean
  submitting?: boolean
  title: string
  confirmText: string
}>()

const emit = defineEmits<{ submit: [payload: ProposalDispositionPayload]; close: [] }>()

const { reportReasonLabel } = useUserLabels()

const reasonCode = ref<ReportReason>('ABUSE')
const detail = ref('')

// 每次打开都回到初始态，避免上一轮的选择残留到下一次提案
watch(
  () => props.open,
  (open) => {
    if (open) {
      reasonCode.value = 'ABUSE'
      detail.value = ''
    }
  },
)

function handleSubmit(): void {
  emit('submit', {
    reasonCode: reasonCode.value,
    // 只填空白视为没填：trim 后空串转 null，避免存入无意义的空格
    detail: detail.value.trim() || null,
  })
}

/** Enter 确认（Esc 由 el-dialog 默认关闭）；多行说明里的 Enter 是换行，不能当确认 */
function handleEnter(event: KeyboardEvent): void {
  if ((event.target as HTMLElement).tagName === 'TEXTAREA') {
    return
  }
  event.preventDefault()
  handleSubmit()
}
</script>

<template>
  <el-dialog
    :model-value="props.open"
    :title="props.title"
    width="480px"
    @update:model-value="emit('close')"
  >
    <div class="flex flex-col gap-4" @keydown.enter="handleEnter">
      <FormField label="违规类型">
        <select v-model="reasonCode" class="ql-field">
          <option v-for="reason in REASONS" :key="reason" :value="reason">
            {{ reportReasonLabel(reason) }}
          </option>
        </select>
      </FormField>

      <FormField label="补充说明" hint="可选，会随理由展示给作者">
        <BaseTextarea v-model="detail" :rows="3" :maxlength="400" placeholder="说明处置依据" />
      </FormField>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
          取消
        </BaseButton>
        <BaseButton variant="danger" size="sm" :loading="props.submitting" @click="handleSubmit">
          {{ props.confirmText }}
        </BaseButton>
      </div>
    </template>
  </el-dialog>
</template>
