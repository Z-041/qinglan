<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import AnnouncementReader from '@shared/components/AnnouncementReader.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import DialogShell from '@/components/admin/DialogShell.vue'
import { useReadCountdown } from '@shared/composables/useReadCountdown'

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

/** 每次打开自增，作为 Reader 的 key 强制重挂载，清掉其内部「已滚到底」状态 */
const sessionKey = ref(0)

/**
 * 倒计时与三条件门控复用学生端同一份纯逻辑（shared/useReadCountdown），
 * 只保留「预览不落库」这层差异。
 */
const gate = useReadCountdown({
  requireScrollToEnd: () => props.requireScrollToEnd,
  requireConfirm: () => props.requireConfirm,

})

function start(): void {
  sessionKey.value += 1
  gate.start(Math.max(0, Math.floor(props.minReadSeconds)))
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      start()
    } else {
      gate.stop()
    }
  },
  { immediate: true },
)

/** 与学生端一致：时长满、必要时已滚到底、必要时已勾选，三者都满足才可关闭 */
const canClose = gate.canClose

const closeLabel = computed(() =>
  canClose.value ? '我知道了' : `${gate.remainingSeconds.value}s 后可关闭`,
)
</script>

<template>
  <DialogShell
    :open="props.open"
    title="学生视角强读预览"
    panel-class="max-w-[720px]"
    hide-footer
    :enter-to-confirm="false"
    @close="emit('close')"
  >
    <p class="mb-3 text-xs text-muted">
      以下为学生端强制阅读弹窗的实际呈现，含最短阅读时长倒计时与「我知道了」按钮的可用性。
      预览不会发布，也不改变发布流程。
    </p>

    <!-- 与学生端弹窗同一高度口径：限高 + flex 链，长文在 Reader 正文区内滚动；
         Reader 已无卡片 chrome，el-dialog 自身就是唯一面板，不再「卡里卡」 -->
    <div class="flex max-h-[68vh] flex-col">
      <AnnouncementReader
        :key="sessionKey"
        class="min-h-0 flex-1"
        :title="props.title"
        :content="props.content"
        :min-read-seconds="props.minReadSeconds"
        :remaining-seconds="gate.remainingSeconds.value"
        :require-scroll-to-end="props.requireScrollToEnd"
        :require-confirm="props.requireConfirm"
        :confirmed="gate.confirmed.value"
        :scrolled-to-end="gate.scrolledToEnd.value"
        @update:confirmed="gate.setConfirmed"
        @update:scrolled-to-end="gate.noteScrolledToEnd"
      >
        <template #footer>
          <BaseButton variant="ghost" size="sm" :disabled="!canClose" @click="emit('close')">
            {{ closeLabel }}
          </BaseButton>
        </template>
      </AnnouncementReader>
    </div>
  </DialogShell>
</template>
