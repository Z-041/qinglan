<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { ReportReason } from '@shared/types/review-common'
import type { ProposalDispositionPayload } from '@/types/review'
import DialogShell from './DialogShell.vue'

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

/** 下拉控件用字符串；ReportReason 是字符串字面量联合，进出各转一次，集中在此 */
const reasonModel = computed<string>({
  get: () => reasonCode.value,
  set: (value) => {
    reasonCode.value = value as ReportReason
  },
})

/** 选项：文案走统一的中文映射（useUserLabels），不在模板里散落 */
const REASON_OPTIONS = REASONS.map((reason) => ({ value: reason, label: reportReasonLabel(reason) }))

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
</script>

<template>
  <DialogShell
    :open="props.open"
    :title="props.title"
    :confirm-text="props.confirmText"
    confirm-variant="danger"
    :submitting="props.submitting"
    @close="emit('close')"
    @confirm="handleSubmit"
  >
    <div class="flex flex-col gap-4">
      <FormField label="违规类型">
        <BaseSelect v-model="reasonModel" :options="REASON_OPTIONS" />
      </FormField>

      <FormField label="补充说明" hint="可选，会随理由展示给作者">
        <BaseTextarea v-model="detail" :rows="3" :maxlength="400" placeholder="说明处置依据" />
      </FormField>
    </div>
  </DialogShell>
</template>
