<script setup lang="ts">
import { ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import ModalShell from '@/components/ModalShell.vue'
import TextButton from '@/components/TextButton.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { ReportReason } from '@shared/types/review-common'

const props = withDefaults(
  defineProps<{
    open: boolean
    /** 举报对象的展示文案，如「这条帖子」「这条评论」 */
    label: string
    submitting?: boolean
  }>(),
  { submitting: false },
)

const emit = defineEmits<{
  close: []
  submit: [reasonCode: ReportReason, detail: string]
}>()

/** 展示顺序即数组顺序：高频理由在前，OTHER 兜底 */
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

const { reportReasonLabel } = useUserLabels()

const reasonCode = ref<ReportReason | null>(null)
const detail = ref('')

/**
 * 关闭的唯一入口：提交中一律不关（遮罩、头部关闭、取消三个出口口径一致），
 * 避免请求在途时把弹窗撤掉、用户既看不到结果也不知道提交去了哪里。
 * ModalShell 的遮罩 / ESC 出口由 close-on-backdrop / close-on-esc 在模板里同步该判定。
 */
function requestClose(): void {
  if (props.submitting) {
    return
  }
  emit('close')
}

// 每次打开都回到初始态（避免上一轮的选择残留）；焦点收入由 ModalShell 负责
watch(
  () => props.open,
  (open) => {
    if (!open) {
      return
    }
    reasonCode.value = null
    detail.value = ''
  },
)

function handleSubmit(): void {
  if (!reasonCode.value) {
    return
  }
  emit('submit', reasonCode.value, detail.value)
}
</script>

<template>
  <ModalShell
    :open="props.open"
    label="举报"
    :close-on-backdrop="!props.submitting"
    :close-on-esc="!props.submitting"
    @close="requestClose"
  >
    <div class="flex items-start justify-between gap-3">
      <div>
        <h2 class="text-sm font-semibold text-ink">举报{{ props.label }}</h2>
        <p class="mt-0.5 text-xs text-muted">举报人身份不会展示给被举报人</p>
      </div>
      <TextButton tone="ink" class="shrink-0" :disabled="props.submitting" @click="requestClose">
        关闭
      </TextButton>
    </div>

    <fieldset class="mt-3">
      <legend class="ql-label">举报理由</legend>
      <div class="grid grid-cols-2 gap-2">
        <label v-for="reason in REASONS" :key="reason" class="cursor-pointer">
          <input
            v-model="reasonCode"
            class="peer sr-only"
            type="radio"
            name="report-reason"
            :value="reason"
          />
          <span
            class="block rounded-lg border border-line px-3 py-2 text-center text-xs text-muted transition peer-checked:(border-primary bg-primary-soft font-medium text-primary-deep) peer-focus-visible:ring-2 peer-focus-visible:ring-primary peer-focus-visible:ring-offset-1"
          >
            {{ reportReasonLabel(reason) }}
          </span>
        </label>
      </div>
    </fieldset>

    <div class="mt-3">
      <label class="ql-label" for="report-detail">补充说明（选填）</label>
      <BaseTextarea
        id="report-detail"
        v-model="detail"
        :rows="3"
        :maxlength="500"
        placeholder="补充具体情况，便于我们核实…"
      />
    </div>

    <div class="mt-4 flex justify-end gap-2">
      <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="requestClose">
        取消
      </BaseButton>
      <BaseButton
        size="sm"
        :loading="props.submitting"
        :disabled="!reasonCode"
        @click="handleSubmit"
      >
        提交举报
      </BaseButton>
    </div>
  </ModalShell>
</template>
