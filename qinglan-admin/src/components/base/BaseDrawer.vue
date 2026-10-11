<script setup lang="ts">
import { ref } from 'vue'

import BaseIcon from '@shared/components/base/BaseIcon.vue'
import { useFocusTrap } from '@shared/composables/useFocusTrap'

/**
 * 自绘右侧抽屉（替代 el-drawer）。用于审核上下文这类「不脱离列表、临时看详情」的场合。
 *
 * 遮罩 + 右贴边面板 + ESC / 遮罩关闭 + 焦点陷阱（复用 shared 的 useFocusTrap）。
 * 只支持 PC：管理端不做移动适配，故无贴底 / 手势逻辑。
 *
 * @param size 面板宽度（CSS 值，如 '480px'）；窄视口下受 w-full 约束
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    size?: string
    /** 遮罩点击与 ESC 是否都能关闭；提交在途等场景传 false */
    dismissible?: boolean
  }>(),
  { size: '480px', dismissible: true },
)

const emit = defineEmits<{ close: [] }>()

const panelRef = ref<HTMLElement | null>(null)

useFocusTrap(panelRef, () => props.open)
</script>

<template>
  <div
    v-if="props.open"
    class="fixed inset-0 z-overlay overscroll-contain"
    role="dialog"
    aria-modal="true"
    :aria-label="props.title"
  >
    <div class="absolute inset-0 bg-black/40" @click="props.dismissible && emit('close')" />
    <aside
      ref="panelRef"
      tabindex="-1"
      :style="{ maxWidth: props.size }"
      class="absolute inset-y-0 right-0 flex w-full flex-col border-l border-line bg-surface shadow-xl outline-none"
      @keydown.escape="props.dismissible && emit('close')"
    >
      <header class="flex items-center justify-between gap-4 border-b border-line px-4 py-3">
        <h2 class="text-sm font-semibold text-ink">{{ props.title }}</h2>
        <button
          type="button"
          class="cursor-pointer rounded-lg p-1 text-muted transition hover:bg-primary-tint hover:text-ink"
          aria-label="关闭"
          @click="emit('close')"
        >
          <BaseIcon name="close" class="size-5" />
        </button>
      </header>
      <div class="flex-1 overflow-y-auto overscroll-contain p-4">
        <slot />
      </div>
    </aside>
  </div>
</template>
