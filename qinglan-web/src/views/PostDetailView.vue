<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import CommentItem from '@/components/CommentItem.vue'
import { usePostDetail } from '@/composables/usePostDetail'
import { useUserStore } from '@/stores/user'
import type { Comment } from '@/types/comment'

const route = useRoute()
const userStore = useUserStore()

const postId = Number(route.params.id)
const {
  post,
  comments,
  loading,
  submitting,
  load,
  toggleLike,
  toggleCommentLike,
  submitComment,
  removeComment,
} = usePostDetail(postId)

const commentText = ref('')
const replyTo = ref<Comment | null>(null)

/** contact 非空即服务端已判定可见，前端不再自行判断（不变量 #2） */
const contactText = computed(() => post.value?.contact ?? '')

onMounted(() => {
  void load()
})

function startReply(comment: Comment): void {
  replyTo.value = comment
}

async function handleComment(): Promise<void> {
  const text = commentText.value.trim()
  if (!text) {
    return
  }
  const ok = await submitComment(text, replyTo.value?.id ?? null)
  if (ok) {
    commentText.value = ''
    replyTo.value = null
  }
}

async function handleDeleteComment(commentId: number): Promise<void> {
  if (!window.confirm('确定删除这条评论吗？')) {
    return
  }
  if (replyTo.value?.id === commentId) {
    replyTo.value = null
  }
  await removeComment(commentId)
}
</script>

<template>
  <section class="py-2">
    <RouterLink class="text-xs text-muted transition hover:text-primary-deep" to="/">
      ← 返回首页
    </RouterLink>

    <p v-if="loading" class="mt-6 text-center text-sm text-muted">加载中…</p>

    <template v-else-if="post">
      <BaseCard class="mt-3">
        <div class="flex items-center gap-2">
          <span class="ql-badge bg-primary-soft text-primary-deep">{{ post.topicName }}</span>
          <span class="truncate text-xs text-muted">{{ post.anonymousCode }}</span>
          <span v-if="post.mine" class="ql-badge shrink-0 bg-primary-tint text-primary-deep">我</span>
          <time class="ml-auto shrink-0 text-xs text-muted" :datetime="post.createdAt">
            {{ new Date(post.createdAt).toLocaleString() }}
          </time>
        </div>

        <p class="mt-3 text-sm leading-relaxed break-words whitespace-pre-wrap text-ink">
          {{ post.content }}
        </p>

        <div
          v-if="post.images.length"
          class="mt-3 grid gap-1.5"
          :class="post.images.length === 1 ? 'grid-cols-1' : 'grid-cols-3'"
        >
          <img
            v-for="url in post.images"
            :key="url"
            :src="url"
            alt="帖子配图"
            loading="lazy"
            class="w-full rounded-lg border border-line object-cover"
            :class="post.images.length === 1 ? 'max-h-96' : 'aspect-square'"
          />
        </div>

        <p v-if="post.mine && post.reviewReason" class="mt-3 rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger-deep">
          审核未通过：{{ post.reviewReason }}
        </p>

        <div class="mt-3">
          <p v-if="contactText" class="rounded-lg bg-primary-tint px-3 py-2 text-xs text-primary-deep">
            联系方式：<span class="font-medium">{{ contactText }}</span>
          </p>
          <p v-else-if="!userStore.isAuthenticated" class="text-xs text-muted">
            <RouterLink class="text-primary-deep" to="/login">认证后</RouterLink>可查看联系方式
          </p>
          <p v-else class="text-xs text-muted">作者未提供联系方式</p>
        </div>

        <div class="mt-4 flex items-center gap-4 border-t border-line pt-3">
          <button
            type="button"
            class="flex cursor-pointer items-center gap-1.5 text-sm transition"
            :class="post.liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
            :aria-pressed="post.liked"
            @click="toggleLike()"
          >
            <svg
              viewBox="0 0 24 24"
              :fill="post.liked ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.8"
              class="size-4"
              aria-hidden="true"
            >
              <path
                d="M12 20.3 4.8 13a4.6 4.6 0 0 1 6.5-6.5l.7.7.7-.7A4.6 4.6 0 0 1 19.2 13Z"
                stroke-linejoin="round"
              />
            </svg>
            <span>{{ post.likeCount }}</span>
          </button>
          <span class="text-xs text-muted">评论 {{ post.commentCount }}</span>
        </div>
      </BaseCard>

      <BaseCard class="mt-4">
        <h2 class="text-sm font-semibold text-ink">评论</h2>

        <ul v-if="comments.length" class="mt-1 divide-y divide-line">
          <CommentItem
            v-for="comment in comments"
            :key="comment.id"
            :comment="comment"
            @toggle-like="toggleCommentLike"
            @reply="startReply"
            @delete="handleDeleteComment"
          />
        </ul>
        <p v-else class="mt-3 text-xs text-muted">还没有评论，说点什么吧</p>

        <form v-if="userStore.isAuthenticated" class="mt-4" @submit.prevent="handleComment">
          <div v-if="replyTo" class="mb-2 flex items-center gap-2 text-xs text-muted">
            <span>正在回复 {{ replyTo.anonymousCode }}</span>
            <button
              type="button"
              class="cursor-pointer text-primary-deep"
              @click="replyTo = null"
            >
              取消
            </button>
          </div>
          <BaseTextarea
            v-model="commentText"
            :rows="3"
            :maxlength="500"
            placeholder="友善发言，理性讨论"
          />
          <div class="mt-2 flex justify-end">
            <BaseButton
              html-type="submit"
              size="sm"
              :loading="submitting"
              :disabled="!commentText.trim()"
            >
              发表评论
            </BaseButton>
          </div>
        </form>
        <p v-else class="mt-4 text-xs text-muted">
          <RouterLink class="text-primary-deep" to="/login">认证后</RouterLink>可参与评论
        </p>
      </BaseCard>
    </template>
  </section>
</template>
