<script setup lang="ts">
import { computed } from 'vue'

type Variant = 'primary' | 'ghost' | 'danger'
type Size = 'md' | 'sm'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    size?: Size
    block?: boolean
    disabled?: boolean
    loading?: boolean
    /** 原生 button 的 type，默认 button 以避免在表单内意外触发提交 */
    htmlType?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', block: false, disabled: false, loading: false, htmlType: 'button' },
)

const emit = defineEmits<{ click: [event: MouseEvent] }>()

const VARIANT_CLASS: Record<Variant, string> = {
  // 填充底用 deep 而非 primary：白字对比度 6.0:1，达标（见 styles/main.css）
  primary: 'bg-primary-deep text-white shadow-sm hover:bg-primary-strong hover:shadow-md',
  ghost: 'border border-line bg-transparent text-muted hover:border-primary hover:bg-primary-tint hover:text-primary-deep',
  danger: 'bg-danger text-white shadow-sm hover:opacity-90',
}

const SIZE_CLASS: Record<Size, string> = {
  md: 'ql-btn-md',
  sm: 'ql-btn-sm',
}

const classes = computed(() => [
  'ql-btn',
  VARIANT_CLASS[props.variant],
  SIZE_CLASS[props.size],
  props.block ? 'w-full' : '',
])
</script>

<template>
  <button
    :type="htmlType"
    class="cursor-pointer"
    :class="classes"
    :disabled="disabled || loading"
    @click="emit('click', $event)"
  >
    <span
      v-if="loading"
      class="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
      aria-hidden="true"
    />
    <slot />
  </button>
</template>
