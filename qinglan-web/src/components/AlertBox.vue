<script setup lang="ts">
/**
 * 行内提示条：圆角浅底 + 同色系深字，三档语气（danger / warning / primary）。
 *
 * 只承载**纯文本提示**——底色的语义配对称对（`bg-*-soft` 配 `text-*-deep` / `text-warning`）
 * 集中在此，避免各处各写一半、改了一边漏另一边。含多段落或表单控件的容器（下架申诉面板、
 * 草稿横幅）不适用：那类结构不该塞进 `<p>`。
 *
 * 间距（mt-*）、断点显隐等由调用方 class 覆盖。
 */
withDefaults(defineProps<{ tone?: 'danger' | 'warning' | 'primary' }>(), { tone: 'danger' })

const TONE_CLASS: Record<'danger' | 'warning' | 'primary', string> = {
  danger: 'bg-danger-soft text-danger-deep',
  warning: 'bg-warning-soft text-warning',
  primary: 'bg-primary-tint text-primary-deep',
}
</script>

<template>
  <p class="rounded-lg px-3 py-2 text-xs" :class="TONE_CLASS[tone]">
    <slot />
  </p>
</template>
