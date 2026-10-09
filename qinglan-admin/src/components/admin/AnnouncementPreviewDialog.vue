<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import AnnouncementReader from '@shared/components/AnnouncementReader.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'

/**
 * 公告「学生视角强读预览」（阶段 D）。
 *
 * 公告发布是全员强制弹窗、不可逆触达，发布前应能看清学生端实际呈现：标题 / 正文排版 /
 * 最短阅读时长倒计时 / 关闭（跳过）按钮的可用性。正文等呈现部分复用学生端同一份
 * `@shared/components/AnnouncementReader`，两端不会漂移。
 *
 * 只做「预览」：不落库、不改发布流程；弹窗自带的关闭（× / ESC）始终可用，避免预览把管理员
 * 困在倒计时里——预览的关闭按钮只是**演示**学生端的可用性判定。
 */
const props = defineProps<{
  open: boolean
  title: string
  content: string
  /** 最短阅读秒数（表单留空时由调用方折算成后端默认值后传入） */
  minReadSeconds: number
  requireScrollToEnd: boolean
  requireConfirm: boolean
}>()

const emit = defineEmits<{ close: [] }>()

const remainingSeconds = ref(0)
const scrolledToEnd = ref(false)
const confirmed = ref(false)
/** 每次打开自增，作为 Reader 的 key 强制重挂载，清掉其内部「已滚到底」状态 */
const sessionKey = ref(0)

let countdownTimer: ReturnType<typeof setInterval> | null = null
let deadlineAt = 0

function stopCountdown(): void {
  if (countdownTimer !== null) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function tickCountdown(): void {
  const left = Math.max(0, Math.ceil((deadlineAt - Date.now()) / 1000))
  remainingSeconds.value = left
  if (left === 0) {
    stopCountdown()
  }
}

function start(): void {
  stopCountdown()
  scrolledToEnd.value = false
  confirmed.value = false
  sessionKey.value += 1
  deadlineAt = Date.now() + Math.max(0, Math.floor(props.minReadSeconds)) * 1000
  tickCountdown()
  if (remainingSeconds.value > 0) {
    countdownTimer = setInterval(tickCountdown, 1000)
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      start()
    } else {
      stopCountdown()
    }
  },
  { immediate: true },
)

onBeforeUnmount(stopCountdown)

/** 与学生端一致：时长满、必要时已滚到底、必要时已勾选，三者都满足才可关闭 */
const canClose = computed(() => {
  if (remainingSeconds.value > 0) {
    return false
  }
  if (props.requireScrollToEnd && !scrolledToEnd.value) {
    return false
  }
  if (props.requireConfirm && !confirmed.value) {
    return false
  }
  return true
})

const closeLabel = computed(() =>
  canClose.value ? '我知道了' : `${remainingSeconds.value}s 后可关闭`,
)
</script>

<template>
  <el-dialog
    :model-value="props.open"
    title="学生视角强读预览"
    width="560px"
    @update:model-value="emit('close')"
  >
    <p class="mb-3 text-xs text-muted">
      以下为学生端强制阅读弹窗的实际呈现，含最短阅读时长倒计时与「我知道了」按钮的可用性。
      预览不会发布，也不改变发布流程。
    </p>

    <AnnouncementReader
      :key="sessionKey"
      :title="props.title"
      :content="props.content"
      :min-read-seconds="props.minReadSeconds"
      :remaining-seconds="remainingSeconds"
      :require-scroll-to-end="props.requireScrollToEnd"
      :require-confirm="props.requireConfirm"
      :confirmed="confirmed"
      @update:confirmed="confirmed = $event"
      @scrolled-to-end="scrolledToEnd = true"
    >
      <template #footer>
        <BaseButton variant="ghost" :disabled="!canClose" @click="emit('close')">
          {{ closeLabel }}
        </BaseButton>
      </template>
    </AnnouncementReader>
  </el-dialog>
</template>
