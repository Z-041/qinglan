<script setup lang="ts">
/**
 * 行内状态行：抽屉 / Tab 内部（非整块区域）的「加载中 / 失败 / 空」文案。
 *
 * 与 StatePanel 的分工：StatePanel 是**整块**四态（带虚线框、可重试）；本组件只出一行灰字，
 * 用在不该再套一层框的位置（抽屉 Tab、列表内），并把「加载中…」这一文案统一在此。
 */
withDefaults(defineProps<{ loading?: boolean }>(), { loading: false })
</script>

<template>
  <p class="text-sm text-muted">
    <template v-if="loading">加载中…</template>
    <slot v-else />
  </p>
</template>
