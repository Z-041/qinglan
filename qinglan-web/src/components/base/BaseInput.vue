<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'text' | 'email' | 'password'
    placeholder?: string
    autocomplete?: string
    inputmode?: 'text' | 'numeric' | 'email'
    maxlength?: number
    invalid?: boolean
  }>(),
  { type: 'text', invalid: false },
)

const model = defineModel<string>({ default: '' })

defineEmits<{ enter: [] }>()
</script>

<template>
  <input
    v-model="model"
    :type="type"
    :placeholder="placeholder"
    :autocomplete="autocomplete"
    :inputmode="inputmode"
    :maxlength="maxlength"
    class="ql-field"
    :class="invalid ? '!border-danger' : ''"
    :aria-invalid="invalid"
    @keyup.enter="$emit('enter')"
  />
</template>
