<script setup lang="ts">
/**
 * 加载中转圈（CSS 描边圆，随 currentColor 着色）。尺寸由 class 决定，默认 size-4。
 * 装饰性元素固定 aria-hidden；加载状态的可访问名应由所在按钮的 disabled/文案承担。
 */
withDefaults(defineProps<{ sizeClass?: string }>(), { sizeClass: 'size-4' })
</script>

<template>
  <span
    class="inline-block animate-spin rounded-full border-2 border-current border-t-transparent"
    :class="sizeClass"
    aria-hidden="true"
  />
</template>
