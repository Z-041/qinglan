<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

/**
 * 自绘点击弹出层（替代 el-popover）：点击 trigger 开关，点外部或 ESC 关闭。
 *
 * 两个插槽：
 * - `#trigger="{ open, toggle }"`：触发器；把 `toggle` 接到自己的 click 上
 * - 默认插槽 `="{ close }"`：弹出内容；内容里的可点击项应调 `close` 收起
 *
 * 不做定位引擎——本项目只用于「触发器右下角浮一层」这一种场景，绝对定位即可。
 */
const props = withDefaults(
  defineProps<{
    align?: 'start' | 'end'
    /** 内容区宽度（px） */
    width?: number
  }>(),
  { align: 'end' },
)

const open = ref(false)

const rootRef = ref<HTMLElement | null>(null)

function toggle(): void {
  open.value = !open.value
}

function close(): void {
  open.value = false
}

function onPointerDown(event: MouseEvent): void {
  if (rootRef.value?.contains(event.target as Node)) {
    return
  }
  close()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  }
}

watch(open, (isOpen) => {
  if (isOpen) {
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeydown)
  } else {
    document.removeEventListener('mousedown', onPointerDown)
    document.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="rootRef" class="relative">
    <slot name="trigger" :open="open" :toggle="toggle" />
    <div
      v-if="open"
      :style="{ width: props.width ? `${props.width}px` : undefined }"
      class="absolute top-full z-overlay mt-2 rounded-xl border border-line bg-surface p-1.5 shadow-xl"
      :class="props.align === 'end' ? 'right-0' : 'left-0'"
    >
      <slot :close="close" />
    </div>
  </div>
</template>
