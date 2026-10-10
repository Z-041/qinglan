<script setup lang="ts">
/**
 * 全站唯一的内联图标集（描边风格，随 currentColor 着色）。
 *
 * 不引第三方图标库：用到的图形少且稳定，内联 SVG 零额外体积、无网络请求、CSP 下安全。
 * 尺寸与颜色一律由调用方的 class 决定（如 size-4 text-primary-deep），组件不给死值；
 * 图标均为装饰性图形，固定 aria-hidden——语义由所在按钮 / 链接的 aria-label 承担。
 */
withDefaults(
  defineProps<{
    name: 'heart' | 'comment' | 'bell' | 'plus'
    /** 填充态（目前仅 heart 使用：已赞为实心）；其余图标恒为描边 */
    filled?: boolean
  }>(),
  { filled: false },
)
</script>

<template>
  <svg
    viewBox="0 0 24 24"
    :fill="name === 'heart' && filled ? 'currentColor' : 'none'"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-if="name === 'heart'" d="M12 20.3 4.8 13a4.6 4.6 0 0 1 6.5-6.5l.7.7.7-.7A4.6 4.6 0 0 1 19.2 13Z" />
    <path
      v-else-if="name === 'comment'"
      d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-5.4A8 8 0 1 1 21 12Z"
    />
    <template v-else-if="name === 'bell'">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </template>
    <template v-else>
      <path d="M12 6v12M6 12h12" />
    </template>
  </svg>
</template>
