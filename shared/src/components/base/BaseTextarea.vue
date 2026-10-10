<script setup lang="ts">
withDefaults(
  defineProps<{
    placeholder?: string
    maxlength?: number
    rows?: number
    invalid?: boolean
    /** 表单字段名 */
    name?: string
    /** 可访问名：没有可见 label 时必须提供 */
    ariaLabel?: string
    spellcheck?: boolean
  }>(),
  { rows: 4, invalid: false, spellcheck: true },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <textarea
    v-model="model"
    :name="name"
    :placeholder="placeholder"
    :maxlength="maxlength"
    :rows="rows"
    :aria-label="ariaLabel"
    :spellcheck="spellcheck"
    class="ql-field resize-y"
    :class="invalid ? '!border-danger' : ''"
    :aria-invalid="invalid"
  />
</template>
