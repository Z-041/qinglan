<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import { useAnnouncement } from '@/composables/useAnnouncement'

/**
 * 全局公告弹窗（强制阅读的体验层，设计方案 5.3）。
 *
 * 硬要求：遮罩点击 / ESC / 浏览器返回都不关闭；倒计时未满关闭按钮 `disabled`；
 * 焦点锁在弹窗内；**不在打开时标记已读**（由 useAnnouncement 把关）。
 */
const {
  current,
  remainingSeconds,
  scrolledToEnd,
  confirmed,
  submitting,
  canClose,
  readFailed,
  closeAndContinue,
  forceClose,
  noteScrolledToEnd,
} = useAnnouncement()

/** 关闭按钮文案：未满足条件时给出倒计时，满足后才是可点的「我知道了」 */
const closeLabel = computed(() =>
  canClose.value ? '我知道了' : `${remainingSeconds.value}s 后可关闭`,
)

/** 顶部进度条宽度：随倒计时推进 */
const progressWidth = computed(() => {
  const announcement = current.value
  if (!announcement || announcement.minReadSeconds <= 0) {
    return '100%'
  }
  const ratio = (announcement.minReadSeconds - remainingSeconds.value) / announcement.minReadSeconds
  return `${Math.min(100, Math.max(0, ratio * 100))}%`
})

const scrollHint = computed(() =>
  current.value?.requireScrollToEnd && !scrolledToEnd.value ? '请滚动到正文末尾' : '',
)

const dialogRef = ref<HTMLElement | null>(null)
const scrollAreaRef = ref<HTMLElement | null>(null)

// 轻量 focus trap：不引第三方库，只在本文件内循环 Tab
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusableElements(): HTMLElement[] {
  const root = dialogRef.value
  if (!root) {
    return []
  }
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
}

function moveFocusToDialog(): void {
  const [first] = focusableElements()
  if (first) {
    first.focus()
  } else {
    // 无可聚焦元素时（如关闭按钮尚 disabled）聚焦弹窗容器自身，保证焦点不留在背景
    dialogRef.value?.focus()
  }
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    // 强制阅读：ESC 不关闭
    event.preventDefault()
    return
  }
  if (event.key !== 'Tab') {
    return
  }
  const items = focusableElements()
  const first = items[0]
  const last = items[items.length - 1]
  if (!first || !last) {
    event.preventDefault()
    dialogRef.value?.focus()
    return
  }
  const active = document.activeElement
  const inside = active ? (dialogRef.value?.contains(active) ?? false) : false
  if (event.shiftKey) {
    if (!inside || active === first) {
      event.preventDefault()
      last.focus()
    }
  } else if (!inside || active === last) {
    event.preventDefault()
    first.focus()
  }
}

function handleScroll(): void {
  const area = scrollAreaRef.value
  if (!area) {
    return
  }
  if (area.scrollTop + area.clientHeight >= area.scrollHeight - 1) {
    noteScrolledToEnd()
  }
}

/** 正文不足一屏时无处可滚，直接视为已到底，避免「需滚动」变成永远关不掉 */
function syncScrollRequirement(): void {
  const area = scrollAreaRef.value
  if (area && area.scrollHeight <= area.clientHeight + 1) {
    noteScrolledToEnd()
  }
}

// 浏览器返回键不关闭：开弹窗时压入一个守卫历史条目，popstate 到来时再压回，把「返回」吞掉
let guardPushed = false

function pushGuardEntry(): void {
  history.pushState({ ...history.state, qinglanAnnouncement: true }, '')
  guardPushed = true
}

function handlePopState(): void {
  if (!current.value) {
    guardPushed = false
    return
  }
  pushGuardEntry()
}

function releaseGuardEntry(): void {
  if (!guardPushed) {
    return
  }
  guardPushed = false
  // 移除自己压入的守卫条目；随后的 popstate 因 current 已为空而直接返回
  history.back()
}

function handleClose(): void {
  void closeAndContinue()
}

function handleForceClose(): void {
  void forceClose()
}

watch(
  current,
  async (announcement) => {
    if (announcement) {
      document.addEventListener('keydown', handleKeydown, true)
      window.addEventListener('popstate', handlePopState)
      pushGuardEntry()
      await nextTick()
      moveFocusToDialog()
      syncScrollRequirement()
    } else {
      document.removeEventListener('keydown', handleKeydown, true)
      window.removeEventListener('popstate', handlePopState)
      releaseGuardEntry()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown, true)
  window.removeEventListener('popstate', handlePopState)
})
</script>

<template>
  <!-- z 轴分层：举报弹层 z-overlay < 公告 z-announcement < 全局提示 z-toast。
       低于提示是刻意的：标记已读失败的错误提示要能盖在公告之上。
       完整口径（含 Element Plus 的 2000+ 一档）见 uno.config.ts。 -->
  <Teleport to="body">
    <div
      v-if="current"
      ref="dialogRef"
      class="fixed inset-0 z-announcement flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="announcement-title"
      tabindex="-1"
    >
      <div
        class="flex max-h-[85vh] w-full max-w-lg flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-xl"
      >
        <!-- 顶部进度条：随倒计时推进 -->
        <div class="h-1 w-full bg-primary-tint">
          <div
            class="h-full bg-primary transition-[width] duration-1000 ease-linear"
            :style="{ width: progressWidth }"
          />
        </div>

        <h2 id="announcement-title" class="px-5 pt-4 text-base font-semibold text-ink">
          {{ current.title }}
        </h2>

        <!-- 正文：超出可视高度时出现滚动条，滚到底才满足强制阅读条件 -->
        <div
          ref="scrollAreaRef"
          class="mt-3 min-h-0 flex-1 overflow-y-auto px-5 text-sm leading-relaxed text-ink"
          @scroll="handleScroll"
        >
          <p class="whitespace-pre-wrap break-words">{{ current.content }}</p>
        </div>

        <div class="mt-4 flex items-end justify-between gap-3 border-t border-line px-5 py-4">
          <div class="flex min-w-0 flex-col gap-1.5">
            <span v-if="scrollHint" class="text-xs text-muted">{{ scrollHint }}</span>
            <label v-if="current.requireConfirm" class="flex cursor-pointer items-center gap-2">
              <input v-model="confirmed" type="checkbox" class="size-4 cursor-pointer accent-primary" />
              <span class="text-xs text-ink">我已了解</span>
            </label>
          </div>

          <!--
            标记已读失败时切出「重试 / 关闭」两个出口：只给一个按钮、失败后又关不掉，会把用户
            永久锁在弹窗里（公告被撤回 → 服务端恒返 404 时必然发生）。这里不渲染任何错误文案
            ——请求错误统一由 http 拦截器 toast（AGENTS.md 第七章），弹窗只负责给出路。
          -->
          <div class="flex shrink-0 items-center gap-2">
            <template v-if="readFailed">
              <BaseButton variant="ghost" :disabled="submitting" @click="handleForceClose">
                关闭
              </BaseButton>
              <BaseButton :loading="submitting" @click="handleClose">重试</BaseButton>
            </template>
            <BaseButton v-else :disabled="!canClose" :loading="submitting" @click="handleClose">
              {{ closeLabel }}
            </BaseButton>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
