<script setup lang="ts">
import BaseIcon from '@shared/components/base/BaseIcon.vue'

/**
 * 点赞按钮（帖子 / 评论统一外观）：心形图标 + 计数。
 *
 * 纯展示组件：只接 props/emit，不发请求；请求与状态回写由 useLikeToggle 在 composable
 * 层完成。label 必填——按钮只有图标与数字，屏幕阅读器必须靠 aria-label 区分
 * 「点赞」与「取消点赞」，评论场景还应带上对方代号。
 */
withDefaults(
  defineProps<{
    liked: boolean
    count: number
    /** 完整可访问名，如「点赞」「取消点赞」「点赞 银杏… 的评论」 */
    label: string
    size?: 'sm' | 'md'
  }>(),
  { size: 'md' },
)

defineEmits<{ toggle: [] }>()
</script>

<template>
  <button
    type="button"
    class="flex cursor-pointer items-center gap-1 text-xs transition"
    :class="liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
    :aria-pressed="liked"
    :aria-label="label"
    @click="$emit('toggle')"
  >
    <BaseIcon name="heart" :class="size === 'sm' ? 'size-3.5' : 'size-4'" :filled="liked" />
    <span>{{ count }}</span>
  </button>
</template>
