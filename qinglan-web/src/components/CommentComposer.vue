<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import type { Comment } from '@/types/comment'

/**
 * 评论输入区（纯展示 + 上抛事件）：未认证时给出登录引导；已认证时渲染「回复对象」条与输入框。
 *
 * 输入内容与「正在回复谁」都由父级持有（成功后才清空的节奏在父级），此处只做受控透传。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    /** 正在回复的目标；为 null 即发一级评论 */
    replyTo?: Comment | null
    authenticated: boolean
    submitting?: boolean
  }>(),
  { replyTo: null, submitting: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
  'cancel-reply': []
}>()
</script>

<template>
  <form v-if="props.authenticated" class="mt-4" @submit.prevent="emit('submit')">
    <div v-if="props.replyTo" class="mb-2 flex items-center gap-2 text-xs text-muted">
      <span>正在回复 {{ props.replyTo.anonymousCode }}</span>
      <button
        type="button"
        class="cursor-pointer text-primary-deep transition-colors hover:text-primary-strong"
        @click="emit('cancel-reply')"
      >
        取消
      </button>
    </div>
    <BaseTextarea
      :model-value="props.modelValue"
      name="comment"
      aria-label="评论内容"
      :rows="3"
      :maxlength="500"
      placeholder="友善发言，理性讨论"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <div class="mt-2 flex justify-end">
      <BaseButton
        html-type="submit"
        size="sm"
        :loading="props.submitting"
        :disabled="!props.modelValue.trim()"
      >
        发表评论
      </BaseButton>
    </div>
  </form>
  <p v-else class="mt-4 text-xs text-muted">
    <RouterLink class="text-primary-deep" to="/login">认证后</RouterLink>可参与评论
  </p>
</template>
