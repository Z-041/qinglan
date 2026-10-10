<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import AnnouncementPreviewDialog from '@/components/admin/AnnouncementPreviewDialog.vue'
import DialogShell from '@/components/admin/DialogShell.vue'
import { toInstantOrNull } from '@/composables/datetime'
import BaseButton from '@shared/components/base/BaseButton.vue'
import FormField from '@shared/components/base/FormField.vue'
import type { AnnouncementLevel, CreateAnnouncementPayload } from '@/types/admin'
import { ANNOUNCEMENT_LEVEL_LABELS } from '@/types/admin'

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

const title = ref('')
const content = ref('')
const level = ref<AnnouncementLevel>('NORMAL')
/** el-date-picker 清空时置 null，故类型含 null（toInstantOrNull 已容忍） */
const startAt = ref<string | null>(null)
const endAt = ref<string | null>(null)
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
      startAt.value = null
      endAt.value = null
      minReadSeconds.value = ''
      requireScrollToEnd.value = false
      requireConfirm.value = false
      previewOpen.value = false
    }
  },
)

/**
 * 秒数用 el-input（v-model 天然给字符串）；时间用 el-date-picker（v-model 给字符串或 null）。
 * 原先为绕过原生 type="number" / datetime-local 的 v-model 类型漂移而写的
 * `:value + @input` 收敛函数已不需要，随之删除。
 */
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

/** 结束时间必须晚于开始时间；任一端留空表示长期有效（开始时间留空由 canSubmit 拦截） */
const periodValid = computed(() => {
  const start = startAt.value
  const end = endAt.value
  if (!start || !end) {
    return true
  }
  return new Date(end).getTime() > new Date(start).getTime()
})

const canSubmit = computed(
  () =>
    title.value.trim().length > 0 &&
    content.value.trim().length > 0 &&
    startAt.value !== null &&
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

function handleSubmit(): void {
  // 开始时间是必填（canSubmit 已保证非空），这里再兜一次非法日期，避免把坏值发给后端
  const startAtInstant = toInstantOrNull(startAt.value)
  if (!canSubmit.value || startAtInstant === null) {
    return
  }
  emit('submit', {
    title: title.value.trim(),
    content: content.value.trim(),
    level: level.value,
    startAt: startAtInstant,
    endAt: toInstantOrNull(endAt.value),
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
    :enter-to-confirm="false"
    @close="emit('close')"
  >
    <div class="flex flex-col gap-4">
      <FormField label="标题">
        <el-input v-model="title" :maxlength="100" placeholder="一句话说明这条公告" />
      </FormField>

      <FormField label="正文">
        <el-input
          v-model="content"
          type="textarea"
          :rows="5"
          :maxlength="5000"
          placeholder="公告正文"
        />
      </FormField>

      <FormField label="等级">
        <el-select v-model="level" class="w-full">
          <el-option
            v-for="value in LEVELS"
            :key="value"
            :label="ANNOUNCEMENT_LEVEL_LABELS[value]"
            :value="value"
          />
        </el-select>
      </FormField>

      <div class="grid grid-cols-2 gap-3">
        <FormField label="生效开始">
          <el-date-picker
            v-model="startAt"
            type="datetime"
            class="w-full"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            placeholder="选择生效时间"
          />
        </FormField>
        <FormField
          label="生效结束"
          :hint="periodValid ? '留空表示长期有效' : ''"
          :error="periodValid ? '' : '结束时间必须晚于开始时间'"
        >
          <el-date-picker
            v-model="endAt"
            type="datetime"
            class="w-full"
            :class="{ 'ql-field-error': !periodValid }"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            placeholder="留空表示长期有效"
          />
        </FormField>
      </div>

      <FormField
        label="最短阅读秒数"
        :hint="minReadValid ? `留空使用后端默认；填写须为 ${MIN_READ_SECONDS}–${MAX_READ_SECONDS} 的整数` : ''"
        :error="minReadValid ? '' : `请输入 ${MIN_READ_SECONDS}–${MAX_READ_SECONDS} 的整数`"
      >
        <el-input
          v-model="minReadSeconds"
          :class="{ 'ql-field-error': !minReadValid }"
          inputmode="numeric"
          placeholder="留空使用后端默认"
        />
      </FormField>

      <!-- 分组用 fieldset/legend（与申诉复审弹窗同口径），不用裸 span 当组标题 -->
      <fieldset class="flex flex-col gap-2">
        <legend class="ql-label">强制方式</legend>
        <el-checkbox v-model="requireScrollToEnd">需滚动到底</el-checkbox>
        <el-checkbox v-model="requireConfirm">需勾选「已了解」</el-checkbox>
      </fieldset>
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
