<script setup lang="ts">
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
      <span v-if="props.comment.mine" class="ql-badge shrink-0 bg-primary-tint text-primary-deep">我</span>
      <time class="ml-auto shrink-0 text-xs text-muted" :datetime="props.comment.createdAt">
        {{ new Date(props.comment.createdAt).toLocaleString() }}
      </time>
    </div>

    <p class="mt-1.5 text-sm leading-relaxed break-words whitespace-pre-wrap text-ink">
      {{ props.comment.content }}
    </p>

    <div class="mt-1.5 flex items-center gap-4">
      <button
        type="button"
        class="flex cursor-pointer items-center gap-1 text-xs transition"
        :class="props.comment.liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
        :aria-pressed="props.comment.liked"
        @click="emit('toggle-like', props.comment.id)"
      >
        <svg
          viewBox="0 0 24 24"
          :fill="props.comment.liked ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.8"
          class="size-3.5"
          aria-hidden="true"
        >
          <path
            d="M12 20.3 4.8 13a4.6 4.6 0 0 1 6.5-6.5l.7.7.7-.7A4.6 4.6 0 0 1 19.2 13Z"
            stroke-linejoin="round"
          />
        </svg>
        <span>{{ props.comment.likeCount }}</span>
      </button>

      <button
        v-if="props.depth === 0"
        type="button"
        class="cursor-pointer text-xs text-muted transition hover:text-primary-deep"
        @click="emit('reply', props.comment)"
      >
        回复
      </button>

      <button
        v-if="props.reportable && !props.comment.mine"
        type="button"
        class="cursor-pointer text-xs text-muted transition hover:text-danger"
        @click="emit('report', props.comment)"
      >
        举报
      </button>

      <button
        v-if="props.comment.mine"
        type="button"
        class="cursor-pointer text-xs text-muted transition hover:text-danger"
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
