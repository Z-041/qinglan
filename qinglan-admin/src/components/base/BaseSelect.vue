<script setup lang="ts">
/**
 * 自绘下拉选择（替代 el-select + el-option）：直接包一层原生 `<select>`，用 `ql-field` 统一外观。
 *
 * 原生 select 自带键盘、读屏与「点外部关闭」的全套行为，是这里最省心也最可靠的选择；
 * 「不限 / 全部」这类可清空项由调用方作为 value='' 的一项写进 options（与 el-option 的写法一致）。
 *
 * @param options 选项数组；value 用于筛选参数，label 为展示文案
 */
withDefaults(
  defineProps<{
    options: readonly { value: string; label: string }[]
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
  <select
    v-model="model"
    :name="name"
    :aria-label="ariaLabel"
    :aria-invalid="invalid"
    class="ql-field cursor-pointer"
    :class="invalid ? '!border-danger' : ''"
  >
    <option v-for="option in options" :key="option.value" :value="option.value">
      {{ option.label }}
    </option>
  </select>
</template>
