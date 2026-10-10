<script setup lang="ts">
/**
 * 「标题 + 内容」信息卡。四处侧栏里 8 张卡片的「ql-card p-4 + 标题样式」逐字相同，
 * 抽到一处，避免改标题字号时漏掉某一页。内容间距与结构由调用方在默认插槽内自控
 * （各卡正文的首行间距 mt-1 / mt-2 / mt-3 不一致，不强行统一）。
 */
defineProps<{ title: string }>()
</script>

<template>
  <div class="ql-card p-4">
    <p class="text-sm font-semibold text-ink">{{ title }}</p>
    <slot />
  </div>
</template>
