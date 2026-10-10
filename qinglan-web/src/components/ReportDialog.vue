<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
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
const dialogRef = ref<HTMLElement | null>(null)

/**
 * 关闭的唯一入口：提交中一律不关（遮罩、头部关闭、取消三个出口口径一致），
 * 避免请求在途时把弹窗撤掉、用户既看不到结果也不知道提交去了哪里。
 */
function requestClose(): void {
  if (props.submitting) {
    return
  }
  emit('close')
}

// 每次打开都回到初始态（避免上一轮的选择残留）并把焦点收进弹窗：
// 容器带 tabindex="-1"，获得焦点后 ESC 才生效，键盘用户也不会把焦点留在背后的页面上
watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }
    reasonCode.value = null
    detail.value = ''
    await nextTick()
    dialogRef.value?.focus()
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
  <!-- z 轴分层：页头 z-header < 本弹层 z-overlay < 公告 z-announcement < 全局提示 z-toast
       （完整口径见 uno.config.ts）。不能与页头同为 50——同值时靠 DOM 顺序决胜负，
       弹层一旦被挪到页头之前就会被盖住。 -->
  <div
    v-if="props.open"
    ref="dialogRef"
    tabindex="-1"
    class="fixed inset-0 z-overlay flex overscroll-contain items-center justify-center bg-black/40 p-4 outline-none max-sm:items-end"
    role="dialog"
    aria-modal="true"
    aria-label="举报"
    @click.self="requestClose"
    @keydown.escape="requestClose"
  >
    <div class="w-full max-w-md rounded-xl border border-line bg-surface p-4 shadow-xl">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-sm font-semibold text-ink">举报{{ props.label }}</h2>
          <p class="mt-0.5 text-xs text-muted">举报人身份不会展示给被举报人</p>
        </div>
        <button
          type="button"
          class="shrink-0 cursor-pointer text-xs text-muted transition hover:text-ink disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:text-muted"
          :disabled="props.submitting"
          @click="requestClose"
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
    </div>
  </div>
</template>
