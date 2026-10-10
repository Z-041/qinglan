<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue'

/**
 * 强制阅读弹窗的**纯呈现**部分（进度条 / 标题 / 正文滚动区 / 滚动到底判定 / 勾选「我已了解」）。
 *
 * 学生端 `AnnouncementModal` 与管理端「发布前学生视角预览」共用这一份，杜绝两处排版与交互漂移
 * （`qinglan-admin` 不得 import `qinglan-web`，只能共用 shared 组件）。
 *
 * 只管「看起来、摸起来一致」，不含业务语义：倒计时数值、能否关闭、关闭后做什么一律由调用方决定
 * （`remainingSeconds` / `canClose` 与 `#footer` 插槽），所以它不依赖 `useAnnouncement`。
 */
const props = defineProps<{
  title: string
  content: string
  /** 最短阅读秒数：仅用于折算顶部进度条比例 */
  minReadSeconds: number
  /** 当前剩余秒数：由调用方倒计时，本组件只负责展示 */
  remainingSeconds: number
  requireScrollToEnd: boolean
  requireConfirm: boolean
  confirmed: boolean
}>()

const emit = defineEmits<{ 'update:confirmed': [boolean]; 'scrolled-to-end': [] }>()

/** 标题 id 唯一化：同一页面可能同时存在学生弹窗与管理端预览两个 Reader，硬编码 id 会冲突 */
const titleId = `announcement-title-${useId()}`

/** 顶部进度条宽度：随倒计时推进 */
const progressWidth = computed(() => {
  if (props.minReadSeconds <= 0) {
    return '100%'
  }
  const ratio = (props.minReadSeconds - props.remainingSeconds) / props.minReadSeconds
  return `${Math.min(100, Math.max(0, ratio * 100))}%`
})

/** 「已滚到底」只在组件内部用于提示文案，判定结果同时抛给调用方去决定能否关闭 */
const scrolledToEnd = ref(false)

const scrollHint = computed(() =>
  props.requireScrollToEnd && !scrolledToEnd.value ? '请滚动到正文末尾' : '',
)

const scrollAreaRef = ref<HTMLElement | null>(null)

function markScrolledToEnd(): void {
  if (scrolledToEnd.value) {
    return
  }
  scrolledToEnd.value = true
  emit('scrolled-to-end')
}

function handleScroll(): void {
  const area = scrollAreaRef.value
  if (area && area.scrollTop + area.clientHeight >= area.scrollHeight - 1) {
    markScrolledToEnd()
  }
}

/** 正文不足一屏时无处可滚，直接视为已到底，避免「需滚动」变成永远关不掉 */
function syncScrollRequirement(): void {
  const area = scrollAreaRef.value
  if (area && area.scrollHeight <= area.clientHeight + 1) {
    markScrolledToEnd()
  }
}

function setConfirmed(event: Event): void {
  emit('update:confirmed', (event.target as HTMLInputElement).checked)
}

onMounted(async () => {
  await nextTick()
  syncScrollRequirement()
})

watch(
  () => props.content,
  async () => {
    await nextTick()
    syncScrollRequirement()
  },
)
</script>

<template>
  <!--
    纯呈现骨架：不自带宽度上限 / 边框 / 阴影 / 圆角——尺寸与卡片 chrome 由调用方的外壳决定
    （学生端弹窗自己是卡片壳；管理端预览的外壳是 el-dialog），避免「卡里卡」与宽度被钉死。
    高度链 h-full + min-h-0：由外壳给 max-h，正文滚动区才能在框内滚动而不是把框撑高。
  -->
  <div class="flex h-full min-h-0 w-full flex-col bg-surface">
    <!-- 顶部进度条：随倒计时推进 -->
    <div class="h-1 w-full bg-primary-tint">
      <div
        class="h-full bg-primary transition-[width] duration-1000 ease-linear"
        :style="{ width: progressWidth }"
      />
    </div>

    <h2 :id="titleId" class="px-5 pt-4 text-base font-semibold text-ink">
      {{ props.title }}
    </h2>

    <!-- 正文：超出可视高度时出现滚动条，滚到底才满足强制阅读条件。
         tabindex=0 让键盘用户也能聚焦并滚动该区域（否则无法滚到底、弹窗永远关不掉）；
         overscroll-contain 防止滚到边界后连锁滚动背后的页面 -->
    <div
      ref="scrollAreaRef"
      tabindex="0"
      class="mt-3 min-h-0 flex-1 overscroll-contain overflow-y-auto px-5 text-sm leading-relaxed text-ink"
      @scroll="handleScroll"
    >
      <p class="whitespace-pre-wrap break-words">{{ props.content }}</p>
    </div>

    <div class="mt-4 flex items-end justify-between gap-3 border-t border-line px-5 py-4">
      <div class="flex min-w-0 flex-col gap-1.5">
        <span v-if="scrollHint" class="text-xs text-muted">{{ scrollHint }}</span>
        <label v-if="props.requireConfirm" class="flex cursor-pointer items-center gap-2">
          <input
            :checked="props.confirmed"
            type="checkbox"
            class="size-4 cursor-pointer accent-primary"
            @change="setConfirmed"
          />
          <span class="text-xs text-ink">我已了解</span>
        </label>
      </div>

      <!-- 底部动作区由调用方注入：学生端是「我知道了 / 重试 / 关闭」，预览端是「关闭预览」 -->
      <slot name="footer" />
    </div>
  </div>
</template>
