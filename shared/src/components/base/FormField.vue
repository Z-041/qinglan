<script setup lang="ts">
defineProps<{
  label: string
  /**
   * 字段级校验文案（如「邮箱格式不正确」），属组件自身的展示职责。
   * 注意：AGENTS.md 第七章的「错误唯一出口」约束的是**请求错误**——那类错误一律由
   * src/api/http.ts 拦截器弹窗，页面不得内联渲染；此处不适用于请求错误。
   */
  error?: string
  hint?: string
}>()
</script>

<template>
  <label class="block">
    <span class="ql-label">{{ label }}</span>
    <slot />
    <span v-if="error" class="ql-error" role="alert">{{ error }}</span>
    <span v-else-if="hint" class="ql-hint">{{ hint }}</span>
  </label>
</template>
