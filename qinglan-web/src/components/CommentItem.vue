<script setup lang="ts">
import RelativeTime from '@shared/components/RelativeTime.vue'
import LikeButton from '@/components/LikeButton.vue'
import type { Comment } from '@/types/comment'

const props = withDefaults(
  defineProps<{
    comment: Comment
    /** 0 = 一级评论，1 = 回复；只支持两级，故回复不再提供「回复」入口 */
    depth?: number
    /**
     * 当前访问者是否具备举报资格（已认证）。
     * 「是不是自己的评论」组件自己知道，但「有没有登录」属于页面态，由父级传入。
     */
    reportable?: boolean
  }>(),
  { depth: 0, reportable: false },
)

const emit = defineEmits<{
  'toggle-like': [commentId: number]
  reply: [comment: Comment]
  delete: [commentId: number]
  report: [comment: Comment]
}>()
</script>

<template>
  <li class="py-3">
    <div class="flex items-center gap-2">
      <span class="truncate text-xs font-medium text-primary-deep">{{ props.comment.anonymousCode }}</span>
      <span v-if="props.comment.mine" class="ql-tag shrink-0 bg-primary-tint text-primary-deep">我</span>
      <RelativeTime
        class="ml-auto shrink-0 text-xs text-muted"
        :iso="props.comment.createdAt"
      />
    </div>

    <p class="mt-1.5 text-sm leading-relaxed break-words whitespace-pre-wrap text-ink">
      {{ props.comment.content }}
    </p>

    <div class="mt-1.5 flex items-center gap-4">
      <LikeButton
        size="sm"
        :liked="props.comment.liked"
        :count="props.comment.likeCount"
        :label="`${props.comment.liked ? '取消点赞' : '点赞'} ${props.comment.anonymousCode} 的评论`"
        @toggle="emit('toggle-like', props.comment.id)"
      />

      <button
        v-if="props.depth === 0"
        type="button"
        class="cursor-pointer text-xs text-muted transition hover:text-primary-deep"
        :aria-label="`回复 ${props.comment.anonymousCode} 的评论`"
        @click="emit('reply', props.comment)"
      >
        回复
      </button>

      <button
        v-if="props.reportable && !props.comment.mine"
        type="button"
        class="cursor-pointer text-xs text-muted transition hover:text-danger"
        :aria-label="`举报 ${props.comment.anonymousCode} 的评论`"
        @click="emit('report', props.comment)"
      >
        举报
      </button>

      <button
        v-if="props.comment.mine"
        type="button"
        class="cursor-pointer text-xs text-muted transition hover:text-danger"
        aria-label="删除我的评论"
        @click="emit('delete', props.comment.id)"
      >
        删除
      </button>
    </div>

    <ul v-if="props.comment.replies.length" class="mt-1 divide-y divide-line border-l-2 border-line pl-3">
      <CommentItem
        v-for="reply in props.comment.replies"
        :key="reply.id"
        :comment="reply"
        :depth="props.depth + 1"
        :reportable="props.reportable"
        @toggle-like="emit('toggle-like', $event)"
        @delete="emit('delete', $event)"
        @report="emit('report', $event)"
      />
    </ul>
  </li>
</template>
