<script setup lang="ts">
import { computed } from 'vue'

/**
 * 文案型动作按钮：无边框、无底色，默认 muted、hover 变色（评论的回复 / 举报 / 删除、
 * 详情的举报、弹窗的关闭）。与 BaseButton 分工不同——BaseButton 是有形按钮
 * （边框 / 底色 / 尺寸档），本组件是行内文字动作，不参与按钮体系的尺寸与语气分档。
 *
 * tone 决定 hover 色；disabled 时统一置灰且不再变 hover 色。aria-label 与额外定位类
 * （如 shrink-0）经属性透传落到根 button，调用方无需包一层。
 */
const props = withDefaults(
  defineProps<{
    tone?: 'primary' | 'danger' | 'ink'
    disabled?: boolean
  }>(),
  { tone: 'primary', disabled: false },
)

const emit = defineEmits<{ click: [event: MouseEvent] }>()

const TONE_HOVER = {
  primary: 'hover:text-primary-deep',
  danger: 'hover:text-danger',
  ink: 'hover:text-ink',
} as const

const hoverClass = computed(() => TONE_HOVER[props.tone])
</script>

<template>
  <button
    type="button"
    class="cursor-pointer text-xs text-muted transition disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:text-muted"
    :class="hoverClass"
    :disabled="disabled"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>
