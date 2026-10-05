<script setup lang="ts">
withDefaults(
  defineProps<{
    placeholder?: string
    maxlength?: number
    rows?: number
    invalid?: boolean
  }>(),
  { rows: 4, invalid: false },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <textarea
    v-model="model"
    :placeholder="placeholder"
    :maxlength="maxlength"
    :rows="rows"
    class="ql-field resize-y"
    :class="invalid ? '!border-danger' : ''"
    :aria-invalid="invalid"
  />
</template>
