<script setup lang="ts">
import { computed } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import ModalShell from '@/components/ModalShell.vue'

/**
 * 学生端二次确认弹层（自绘，替代原生 confirm）。文案与按钮均可配；
 * 面板窄（max-w-sm）、危险操作把确认按钮切到实心危险色。
 * 标题是问句（「确定删除…吗？」），作可访问名不够凝练，故 aria-label 默认另取 label。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    /** 可见标题 */
    title: string
    /** 可访问名；不传时退回标题 */
    label?: string
    message?: string
    confirmText?: string
    cancelText?: string
    danger?: boolean
    submitting?: boolean
  }>(),
  {
    label: '',
    message: '',
    confirmText: '确认',
    cancelText: '取消',
    danger: false,
    submitting: false,
  },
)

const emit = defineEmits<{ confirm: []; close: [] }>()

const ariaLabel = computed(() => props.label || props.title)
</script>

<template>
  <ModalShell
    :open="props.open"
    :label="ariaLabel"
    panel-class="max-w-sm"
    :dismissible="!props.submitting"
    @close="emit('close')"
  >
    <p class="text-sm font-semibold text-ink">{{ props.title }}</p>
    <p v-if="props.message" class="mt-1 text-xs text-muted">{{ props.message }}</p>
    <div class="mt-4 flex justify-end gap-2">
      <BaseButton variant="ghost" size="sm" :disabled="props.submitting" @click="emit('close')">
        {{ props.cancelText }}
      </BaseButton>
      <BaseButton
        :variant="props.danger ? 'danger' : 'primary'"
        size="sm"
        :loading="props.submitting"
        @click="emit('confirm')"
      >
        {{ props.confirmText }}
      </BaseButton>
    </div>
  </ModalShell>
</template>
