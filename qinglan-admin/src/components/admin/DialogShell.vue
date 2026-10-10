<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'

/**
 * 管理端弹窗的统一外壳：el-dialog 面板 + 标准「取消 / 确认」footer + Enter 提交。
 *
 * 八个弹窗（申诉复审 / 驳回举报 / 处置提案 / 超管裁决 / 调分 / 封禁 / 新建公告 / 强读预览）
 * 共用同一套骨架。本组件只管这些**固定骨架**，字段（理由框 / 数字 / 选择器 / 表单）与载荷构造
 * 由调用方经默认插槽和 @confirm 保留，差异不外泄。footer 需要多按钮时用 #footer 覆盖，
 * 完全不需要 footer 时传 hideFooter。
 *
 * Enter 规则：TEXTAREA 内的 Enter 是换行，必须放行；其余元素按 Enter 等同点确认。
 * 有多个提交目标（如裁决的同意/驳回）或不走「单确认」流程（如公告表单）时传 enter-to-confirm=false。
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
    /** 是否启用「非文本域按 Enter 即确认」；双提交目标或无单确认流程的弹窗应关闭 */
    enterToConfirm?: boolean
    /** 完全去掉 footer（如强读预览：关闭按钮在内容区自管，不需要标准取消/确认） */
    hideFooter?: boolean
  }>(),
  {
    width: '480px',
    submitting: false,
    confirmText: '确认',
    cancelText: '取消',
    confirmVariant: 'primary',
    confirmDisabled: false,
    enterToConfirm: true,
    hideFooter: false,
  },
)

const emit = defineEmits<{ close: []; confirm: [] }>()

/**
 * 这些元素上按 Enter 有**原生语义**（textarea 换行、下拉展开、日期/数字提交、勾选、按钮自带点击），
 * 不能当成「确认」——否则 Enter 会同时触发控件行为与提交（此前只放行了 TEXTAREA，
 * 含下拉/日期控件的弹窗需要各自显式关掉 enterToConfirm 才能避免冲突）。
 */
const ENTER_NATIVE_TAGS = new Set(['TEXTAREA', 'SELECT', 'BUTTON', 'A'])
const ENTER_NATIVE_INPUT_TYPES = new Set(['date', 'datetime-local', 'number', 'checkbox', 'radio', 'file'])

/** 其余元素按 Enter 等同点确认 */
function handleEnter(event: KeyboardEvent): void {
  if (!props.enterToConfirm) {
    return
  }
  const target = event.target as HTMLElement
  if (ENTER_NATIVE_TAGS.has(target.tagName)) {
    return
  }
  if (target instanceof HTMLInputElement && ENTER_NATIVE_INPUT_TYPES.has(target.type)) {
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

    <template v-if="!hideFooter && $slots.footer" #footer>
      <slot name="footer" />
    </template>
    <template v-else-if="!hideFooter" #footer>
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
