<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import AnnouncementPreviewDialog from '@/components/admin/AnnouncementPreviewDialog.vue'
import DialogShell from '@/components/admin/DialogShell.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseInput from '@shared/components/base/BaseInput.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'
import type { AnnouncementLevel, CreateAnnouncementPayload } from '@/types/admin'

const props = defineProps<{ open: boolean; submitting?: boolean }>()

const emit = defineEmits<{ submit: [payload: CreateAnnouncementPayload]; close: [] }>()

/** 与后端 announcement/dto/CreateAnnouncementRequest 的 @Min(0) @Max(60) 对齐 */
const MIN_READ_SECONDS = 0
const MAX_READ_SECONDS = 60

/**
 * 表单留空时后端取 `qinglan.announcement.min-read-seconds`（application.example.yml 为 5）。
 * 预览按该默认值折算，避免「预览 0 秒、实际要读 5 秒」的认知漂移。
 */
const DEFAULT_MIN_READ_SECONDS = 5

const LEVELS: AnnouncementLevel[] = ['URGENT', 'NORMAL', 'ACTIVITY']
const LEVEL_LABELS: Record<AnnouncementLevel, string> = {
  URGENT: '紧急',
  NORMAL: '普通',
  ACTIVITY: '活动',
}

const title = ref('')
const content = ref('')
const level = ref<AnnouncementLevel>('NORMAL')
const startAt = ref('')
const endAt = ref('')
const minReadSeconds = ref('')
const requireScrollToEnd = ref(false)
const requireConfirm = ref(false)
/** 「学生视角强读预览」弹窗开关（阶段 D）；预览不落库、不改发布流程 */
const previewOpen = ref(false)

watch(
  () => props.open,
  (open) => {
    if (open) {
      title.value = ''
      content.value = ''
      level.value = 'NORMAL'
      startAt.value = ''
      endAt.value = ''
      minReadSeconds.value = ''
      requireScrollToEnd.value = false
      requireConfirm.value = false
      previewOpen.value = false
    }
  },
)

/**
 * type="number" / datetime-local 上的 v-model 会把值转成非字符串类型，破坏
 * 「表单字段恒为 string」这一前提（P4 已修同类缺陷）。改用 :value + @input 收敛回字符串。
 */
function onStartAtInput(event: Event): void {
  startAt.value = (event.target as HTMLInputElement).value
}

function onEndAtInput(event: Event): void {
  endAt.value = (event.target as HTMLInputElement).value
}

function onMinReadSecondsInput(event: Event): void {
  minReadSeconds.value = (event.target as HTMLInputElement).value
}

const minReadValue = computed(() => Number(minReadSeconds.value))
/** 留空表示用后端默认，视为合法；填了必须是 0–60 的整数 */
const minReadValid = computed(() => {
  if (minReadSeconds.value.trim() === '') {
    return true
  }
  return (
    Number.isInteger(minReadValue.value) &&
    minReadValue.value >= MIN_READ_SECONDS &&
    minReadValue.value <= MAX_READ_SECONDS
  )
})

/** 结束时间必须晚于开始时间；留空表示长期有效。开始时间为空时交给 canSubmit 拦截 */
const periodValid = computed(() => {
  if (!endAt.value.trim() || !startAt.value.trim()) {
    return true
  }
  return new Date(endAt.value).getTime() > new Date(startAt.value).getTime()
})

const canSubmit = computed(
  () =>
    title.value.trim().length > 0 &&
    content.value.trim().length > 0 &&
    startAt.value.trim().length > 0 &&
    minReadValid.value &&
    periodValid.value,
)

/** 标题与正文都填了才允许预览：否则弹出的只是一个空白「强读」弹窗，没有意义 */
const canPreview = computed(
  () => title.value.trim().length > 0 && content.value.trim().length > 0,
)

/** 预览用的最短阅读秒数：填了合法值就用它，否则用后端默认值折算 */
const previewMinReadSeconds = computed(() =>
  minReadSeconds.value.trim() !== '' && minReadValid.value
    ? minReadValue.value
    : DEFAULT_MIN_READ_SECONDS,
)

/** datetime-local 是本地时间且不带时区，后端要的是 Instant，转 ISO-8601 UTC */
function toInstant(value: string): string {
  return new Date(value).toISOString()
}

function handleSubmit(): void {
  if (!canSubmit.value) {
    return
  }
  emit('submit', {
    title: title.value.trim(),
    content: content.value.trim(),
    level: level.value,
    startAt: toInstant(startAt.value),
    endAt: endAt.value.trim() ? toInstant(endAt.value) : null,
    minReadSeconds: minReadSeconds.value.trim() === '' ? null : minReadValue.value,
    requireScrollToEnd: requireScrollToEnd.value,
    requireConfirm: requireConfirm.value,
  })
}
</script>

<template>
  <DialogShell
    :open="props.open"
    title="新建公告"
    width="520px"
    :enter-to-confirm="false"
    @close="emit('close')"
  >
    <div class="flex flex-col gap-4">
      <FormField label="标题">
        <BaseInput v-model="title" :maxlength="100" placeholder="一句话说明这条公告" />
      </FormField>

      <FormField label="正文">
        <BaseTextarea v-model="content" :rows="5" :maxlength="5000" placeholder="公告正文" />
      </FormField>

      <FormField label="等级">
        <select v-model="level" class="ql-field">
          <option v-for="value in LEVELS" :key="value" :value="value">
            {{ LEVEL_LABELS[value] }}
          </option>
        </select>
      </FormField>

      <div class="grid grid-cols-2 gap-3">
        <FormField label="生效开始">
          <input :value="startAt" type="datetime-local" class="ql-field" @input="onStartAtInput" />
        </FormField>
        <FormField
          label="生效结束"
          :hint="periodValid ? '留空表示长期有效' : ''"
          :error="periodValid ? '' : '结束时间必须晚于开始时间'"
        >
          <input
            :value="endAt"
            type="datetime-local"
            class="ql-field"
            :class="!periodValid && endAt.trim() !== '' && startAt.trim() !== '' ? '!border-danger' : ''"
            :aria-invalid="!periodValid && endAt.trim() !== '' && startAt.trim() !== ''"
            @input="onEndAtInput"
          />
        </FormField>
      </div>

      <FormField
        label="最短阅读秒数"
        :hint="minReadValid ? `留空使用后端默认；填写须为 ${MIN_READ_SECONDS}–${MAX_READ_SECONDS} 的整数` : ''"
        :error="minReadValid ? '' : `请输入 ${MIN_READ_SECONDS}–${MAX_READ_SECONDS} 的整数`"
      >
        <input
          :value="minReadSeconds"
          type="number"
          :min="MIN_READ_SECONDS"
          :max="MAX_READ_SECONDS"
          class="ql-field"
          :class="minReadSeconds.trim() !== '' && !minReadValid ? '!border-danger' : ''"
          :aria-invalid="minReadSeconds.trim() !== '' && !minReadValid"
          placeholder="留空使用后端默认"
          @input="onMinReadSecondsInput"
        />
      </FormField>

      <div class="flex flex-col gap-2">
        <span class="ql-label">强制方式</span>
        <label class="flex items-center gap-2 text-sm text-ink">
          <input v-model="requireScrollToEnd" type="checkbox" />
          <span>需滚动到底</span>
        </label>
        <label class="flex items-center gap-2 text-sm text-ink">
          <input v-model="requireConfirm" type="checkbox" />
          <span>需勾选「已了解」</span>
        </label>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
          取消
        </BaseButton>
        <BaseButton variant="ghost" size="sm" :disabled="!canPreview" @click="previewOpen = true">
          预览学生视角
        </BaseButton>
        <BaseButton size="sm" :loading="props.submitting" :disabled="!canSubmit" @click="handleSubmit">
          提交草稿
        </BaseButton>
      </div>
    </template>
  </DialogShell>

  <!-- 发布前先看清学生端的强读效果（阶段 D）。仅前置一步预览，发布流程与校验不变 -->
  <AnnouncementPreviewDialog
    :open="previewOpen"
    :title="title"
    :content="content"
    :min-read-seconds="previewMinReadSeconds"
    :require-scroll-to-end="requireScrollToEnd"
    :require-confirm="requireConfirm"
    @close="previewOpen = false"
  />
</template>
