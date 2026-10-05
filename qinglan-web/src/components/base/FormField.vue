<script setup lang="ts">
defineProps<{
  label: string
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
