<script setup lang="ts">
import { computed, ref } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import ModalShell from '@shared/components/ModalShell.vue'

import { messageBoxState, resolveMessageBox } from '@/composables/useMessageBox'

/**
 * 命令式确认 / 输入弹窗的宿主（单例，挂在 App.vue）。
 *
 * 与 ElMessageBox 同——调用方拿到的是一次性的 Promise，弹窗本身由这个常驻组件渲染。
 * 用 ModalShell 做外壳，因此遮罩 / ESC / 焦点陷阱与全站其它弹层完全一致。
 */
const request = computed(() => messageBoxState.request)

const inputRef = ref<HTMLInputElement | null>(null)

/**
 * Enter 提交的放行规则（与 DialogShell 同口径）：有原生语义的元素不当作确认，
 * 否则 Enter 会同时触发按钮 click 与提交。
 */
const ENTER_NATIVE_TAGS = new Set(['TEXTAREA', 'SELECT', 'BUTTON', 'A'])

function onEnter(event: KeyboardEvent): void {
  const target = event.target as HTMLElement
  if (ENTER_NATIVE_TAGS.has(target.tagName)) {
    return
  }
  event.preventDefault()
  submit()
}

function submit(): void {
  const current = request.value
  if (!current) {
    return
  }
  if (current.kind === 'prompt') {
    const error = current.validate(messageBoxState.input)
    if (error) {
      messageBoxState.error = error
      inputRef.value?.focus()
      return
    }
  }
  resolveMessageBox(true)
}

function onInput(): void {
  if (messageBoxState.error) {
    messageBoxState.error = ''
  }
}
</script>

<template>
  <ModalShell
    v-if="request"
    :open="true"
    :label="request.title"
    panel-class="max-w-sm"
    @close="resolveMessageBox(false)"
  >
    <div @keydown.enter="onEnter">
      <p class="text-sm font-semibold text-ink">{{ request.title }}</p>
      <p v-if="request.message" class="mt-1 text-xs text-muted">{{ request.message }}</p>
      <div v-if="request.kind === 'prompt'" class="mt-3">
        <input
          ref="inputRef"
          v-model="messageBoxState.input"
          type="text"
          :placeholder="request.placeholder"
          :aria-label="request.title"
          :aria-invalid="messageBoxState.error ? true : undefined"
          class="ql-field"
          :class="messageBoxState.error ? '!border-danger' : ''"
          @input="onInput"
        />
        <span v-if="messageBoxState.error" class="ql-error" role="alert">
          {{ messageBoxState.error }}
        </span>
      </div>
      <div class="mt-4 flex justify-end gap-2">
        <BaseButton variant="ghost" size="sm" @click="resolveMessageBox(false)">
          {{ request.cancelText }}
        </BaseButton>
        <BaseButton :variant="request.danger ? 'danger' : 'primary'" size="sm" @click="submit">
          {{ request.confirmText }}
        </BaseButton>
      </div>
    </div>
  </ModalShell>
</template>
