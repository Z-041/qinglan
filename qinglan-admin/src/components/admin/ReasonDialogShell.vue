<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'

/**
 * 管理端「处置理由确认」弹窗的统一外壳：el-dialog 面板 + 标准取消/确认 footer + Enter 提交。
 *
 * 6 个处置弹窗（申诉复审 / 驳回举报 / 处置提案 / 超管裁决 / 调分 / 封禁）原本各自复制同一套
 * 外壳、footer 与 Enter 处理；本组件只管这些**固定骨架**，字段（理由框 / 数字 / 选择器）与
 * 载荷构造由调用方经默认插槽和 @confirm 保留，差异不外泄。
 *
 * Enter 规则：TEXTAREA 内的 Enter 是换行，必须放行；其余元素按 Enter 等同点确认。
 * 有多个提交目标（如裁决的同意/驳回）时传 enter-to-confirm=false 并自定义 #footer。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    width?: string
    submitting?: boolean
    confirmText?: string
    cancelText?: string
    /** 确认按钮配色：常规主色或危险（驳回 / 封禁等） */
    confirmVariant?: 'primary' | 'danger'
    /** 外部校验未过时禁用确认按钮 */
    confirmDisabled?: boolean
    /** 是否启用「非文本域按 Enter 即确认」；双提交目标的弹窗应关闭 */
    enterToConfirm?: boolean
  }>(),
  {
    width: '480px',
    submitting: false,
    confirmText: '确认',
    cancelText: '取消',
    confirmVariant: 'primary',
    confirmDisabled: false,
    enterToConfirm: true,
  },
)

const emit = defineEmits<{ close: []; confirm: [] }>()

/** 与原各弹窗一致的 Enter 处理：TEXTAREA 放行换行，其余拦截并触发确认 */
function handleEnter(event: KeyboardEvent): void {
  if (!props.enterToConfirm) {
    return
  }
  if ((event.target as HTMLElement).tagName === 'TEXTAREA') {
    return
  }
  event.preventDefault()
  if (!props.submitting && !props.confirmDisabled) {
    emit('confirm')
  }
}
</script>

<template>
  <el-dialog
    :model-value="open"
    :title="title"
    :width="width"
    @update:model-value="emit('close')"
  >
    <div @keydown.enter="handleEnter">
      <slot />
    </div>

    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
    <template v-else #footer>
      <div class="flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" :disabled="submitting" @click="emit('close')">
          {{ cancelText }}
        </BaseButton>
        <BaseButton
          :variant="confirmVariant"
          size="sm"
          :loading="submitting"
          :disabled="confirmDisabled"
          @click="emit('confirm')"
        >
          {{ confirmText }}
        </BaseButton>
      </div>
    </template>
  </el-dialog>
</template>
