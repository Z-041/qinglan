<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'text' | 'email'
    placeholder?: string
    autocomplete?: string
    inputmode?: 'text' | 'numeric' | 'email'
    maxlength?: number
    /** 校验失败态：红框 + aria-invalid；通常与外层 FormField 的 error 同步传入 */
    invalid?: boolean
    /** 表单字段名：浏览器自动填充 / 密码管理器 / 表单语义都依赖它 */
    name?: string
    /** 可访问名：外层未用 FormField 关联 label 时必须提供 */
    ariaLabel?: string
    /** 邮箱 / 验证码 / 学号等字段应关闭拼写检查，避免波浪线与自动改写 */
    spellcheck?: boolean
  }>(),
  { type: 'text', invalid: false, spellcheck: true },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <input
    v-model="model"
    :type="type"
    :name="name"
    :placeholder="placeholder"
    :autocomplete="autocomplete"
    :inputmode="inputmode"
    :maxlength="maxlength"
    :aria-label="ariaLabel"
    :spellcheck="spellcheck"
    class="ql-field"
    :class="invalid ? '!border-danger' : ''"
    :aria-invalid="invalid"
  />
</template>
