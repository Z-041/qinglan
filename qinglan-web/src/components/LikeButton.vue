<script setup lang="ts">
import { computed } from 'vue'

import BaseIcon from '@shared/components/base/BaseIcon.vue'

/**
 * 点赞按钮（帖子 / 评论统一外观）：心形图标 + 计数。
 *
 * 纯展示组件：只接 props/emit，不发请求；请求与状态回写由 useLikeToggle 在 composable 层完成。
 * 可访问名默认由 liked 派生（「点赞 / 取消点赞」），评论场景再传 subject（对方代号）拼进去；
 * 按钮只有图标与数字，屏幕阅读器全靠这个 aria-label 区分——故由组件派生，调用方不必各抄一份分支。
 */
const props = withDefaults(
  defineProps<{
    liked: boolean
    count: number
    /** 可选的「对象」，如评论者的匿名代号；拼进可访问名以区分同一页的多个点赞按钮 */
    subject?: string
    /** 覆盖自动生成的可访问名（少数需要更具体措辞的场景） */
    label?: string
    size?: 'sm' | 'md'
  }>(),
  { size: 'md', subject: '', label: '' },
)

defineEmits<{ toggle: [] }>()

const ariaLabel = computed(() => {
  if (props.label) {
    return props.label
  }
  const verb = props.liked ? '取消点赞' : '点赞'
  return props.subject ? `${verb} ${props.subject} 的评论` : verb
})
</script>

<template>
  <button
    type="button"
    class="flex cursor-pointer items-center gap-1 text-xs transition"
    :class="liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
    :aria-pressed="liked"
    :aria-label="ariaLabel"
    @click="$emit('toggle')"
  >
    <BaseIcon name="heart" :class="size === 'sm' ? 'size-3.5' : 'size-4'" :filled="liked" />
    <span>{{ count }}</span>
  </button>
</template>
