<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import AnnouncementReader from '@shared/components/AnnouncementReader.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import { useAnnouncement } from '@/composables/useAnnouncement'

/**
 * 全局公告弹窗（强制阅读的体验层，设计方案 5.3）。
 *
 * 弹窗外壳（遮罩 / 焦点锁 / 防返回）与业务状态留在本组件；标题 / 进度条 / 正文滚动 / 勾选
 * 等**呈现部分**由 `@shared/components/AnnouncementReader` 提供——管理端「发布前预览」也用它，
 * 两端共用一份，杜绝排版漂移。
 *
 * 硬要求：遮罩点击 / ESC / 浏览器返回都不关闭；倒计时未满关闭按钮 `disabled`；
 * 焦点锁在弹窗内；**不在打开时标记已读**（由 useAnnouncement 把关）。
 */
const {
  current,
  remainingSeconds,
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

const dialogRef = ref<HTMLElement | null>(null)

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
  // 先摘监听再释放：否则 history.back() 触发的 popstate 会被 handlePopState 当成「用户按了返回」再压回
  releaseGuardEntry()
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
      class="fixed inset-0 z-announcement flex overscroll-contain items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      :aria-label="current.title"
      tabindex="-1"
    >
      <!-- 卡片壳在弹窗侧：宽度上限 720px（与正文阅读宽一致），短内容贴合、长文展宽，
           超高在 Reader 正文区内滚动；chrome 不再由 shared Reader 自带 -->
      <div
        class="flex max-h-[85vh] w-full max-w-[720px] flex-col overflow-hidden rounded-xl border border-line shadow-xl"
      >
        <AnnouncementReader
          class="min-h-0 flex-1"
          :title="current.title"
          :content="current.content"
          :min-read-seconds="current.minReadSeconds"
          :remaining-seconds="remainingSeconds"
          :require-scroll-to-end="current.requireScrollToEnd"
          :require-confirm="current.requireConfirm"
          :confirmed="confirmed"
          @update:confirmed="confirmed = $event"
          @scrolled-to-end="noteScrolledToEnd"
        >
          <!--
            标记已读失败时切出「重试 / 关闭」两个出口：只给一个按钮、失败后又关不掉，会把用户
            永久锁在弹窗里（公告被撤回 → 服务端恒返 404 时必然发生）。这里不渲染任何错误文案
            ——请求错误统一由 http 拦截器 toast（AGENTS.md 第七章），弹窗只负责给出路。
          -->
          <template #footer>
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
          </template>
        </AnnouncementReader>
      </div>
    </div>
  </Teleport>
</template>
