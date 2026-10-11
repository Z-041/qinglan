<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'
import ModalShell from '@shared/components/ModalShell.vue'

/**
 * 管理端弹窗的统一外壳：ModalShell 面板 + 标题 + 标准「取消 / 确认」footer + Enter 提交。
 *
 * 八个弹窗（申诉复审 / 驳回举报 / 处置提案 / 超管裁决 / 调分 / 封禁 / 新建公告 / 强读预览）
 * 共用同一套骨架。本组件只管这些**固定骨架**，字段（理由框 / 数字 / 选择器 / 表单）与载荷构造
 * 由调用方经默认插槽和 @confirm 保留，差异不外泄。footer 需要多按钮时用 #footer 覆盖，
 * 完全不需要 footer 时传 hideFooter。
 *
 * Enter 规则：有**原生语义**的元素（textarea 换行、下拉展开、日期/数字提交、勾选、按钮自带点击）
 * 放行，其余元素按 Enter 等同点确认。有多个提交目标（如裁决的同意/驳回）或不走「单确认」流程
 * （如公告表单）时传 enter-to-confirm=false。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    /** 面板宽度类；常规表单 480px，宽内容（强读预览）720px */
    panelClass?: string
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
    panelClass: 'max-w-[480px]',
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
  <ModalShell
    :open="props.open"
    :label="props.title"
    :dismissible="!props.submitting"
    :panel-class="props.panelClass"
    @close="emit('close')"
  >
    <h2 class="text-sm font-semibold text-ink">{{ props.title }}</h2>

    <div class="mt-4" @keydown.enter="handleEnter">
      <slot />
    </div>

    <div v-if="!props.hideFooter" class="mt-5 flex justify-end gap-2">
      <slot name="footer">
        <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
          {{ props.cancelText }}
        </BaseButton>
        <BaseButton
          :variant="props.confirmVariant"
          size="sm"
          :loading="props.submitting"
          :disabled="props.confirmDisabled"
          @click="emit('confirm')"
        >
          {{ props.confirmText }}
        </BaseButton>
      </slot>
    </div>
  </ModalShell>
</template>
