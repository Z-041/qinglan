<script setup lang="ts">
import { ref, watch } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import { useUserLabels } from '@/composables/useUserLabels'
import type { ReportReason } from '@/types/review'

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

// 每次重新打开都回到初始态，避免上一轮的选择与说明残留到下一次举报
watch(
  () => props.open,
  (open) => {
    if (open) {
      reasonCode.value = null
      detail.value = ''
    }
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
  <!-- z 轴分层：页头 z-50 < 弹层 z-[900] < 全局提示 z-[1000]。不能与页头同为 50——
       同值时靠 DOM 顺序决胜负，弹层一旦被挪到页头之前就会被盖住。 -->
  <div
    v-if="props.open"
    class="fixed inset-0 z-[900] flex items-end justify-center bg-black/40 p-4 sm:items-center"
    role="dialog"
    aria-modal="true"
    aria-label="举报"
    @click.self="!props.submitting && emit('close')"
  >
    <div class="w-full max-w-md rounded-xl border border-line bg-surface p-4 shadow-xl">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-sm font-semibold text-ink">举报{{ props.label }}</h2>
          <p class="mt-0.5 text-xs text-muted">举报人身份不会展示给被举报人</p>
        </div>
        <button
          type="button"
          class="shrink-0 cursor-pointer text-xs text-muted transition hover:text-ink"
          @click="emit('close')"
        >
          关闭
        </button>
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
              class="block rounded-lg border border-line px-3 py-2 text-center text-xs text-muted transition peer-checked:(border-primary bg-primary-soft font-medium text-primary-deep)"
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
          placeholder="补充具体情况，便于我们核实"
        />
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
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
    </div>
  </div>
</template>
