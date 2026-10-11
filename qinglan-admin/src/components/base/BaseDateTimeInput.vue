<script setup lang="ts">
/**
 * 日期时间输入（替代 el-date-picker 的 datetime 模式）：原生 `<input type="datetime-local">` + `ql-field`。
 *
 * 值是**本地时间**的无时区串（`YYYY-MM-DDTHH:mm`），交给 `composables/datetime.toInstantOrNull`
 * 转成后端要的 Instant——与 el-date-picker 的 `value-format` 口径一致。
 */
withDefaults(
  defineProps<{
    invalid?: boolean
    name?: string
    /** 可访问名：外层无 FormField 关联 label 时提供 */
    ariaLabel?: string
  }>(),
  { invalid: false },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <input
    v-model="model"
    type="datetime-local"
    :name="name"
    :aria-label="ariaLabel"
    :aria-invalid="invalid"
    class="ql-field"
    :class="invalid ? '!border-danger' : ''"
  />
</template>
