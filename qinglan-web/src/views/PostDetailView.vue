<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import CommentItem from '@/components/CommentItem.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import { usePostDetail } from '@/composables/usePostDetail'
import { useReport } from '@/composables/useReport'
import { useToast } from '@/composables/useToast'
import { useUserLabels } from '@/composables/useUserLabels'
import { useUserStore } from '@/stores/user'
import type { Comment } from '@/types/comment'
import type { ReportReason } from '@/types/review'

const route = useRoute()
const userStore = useUserStore()
const { pushToast } = useToast()
const { reviewReasonText } = useUserLabels()

const postId = Number(route.params.id)
const {
  post,
  comments,
  loading,
  failed,
  submitting,
  appealSubmitting,
  appealed,
  load,
  toggleLike,
  toggleCommentLike,
  submitComment,
  removeComment,
  submitAppeal,
} = usePostDetail(postId)

const {
  target: reportTarget,
  submitting: reportSubmitting,
  open: openReport,
  close: closeReport,
  submit: submitReport,
} = useReport()

const commentText = ref('')
const replyTo = ref<Comment | null>(null)
const appealReason = ref('')

/** contact 非空即服务端已判定可见，前端不再自行判断（不变量 #2） */
const contactText = computed(() => post.value?.contact ?? '')

/** 举报入口只对已认证访问者出现：举报人取自登录态，游客点了也只会被弹去登录 */
const reportAllowed = computed(() => userStore.isAuthenticated)

onMounted(() => {
  void load()
})

function startReply(comment: Comment): void {
  replyTo.value = comment
}

function openPostReport(): void {
  if (!post.value) {
    return
  }
  openReport({ targetType: 'POST', targetId: post.value.id, label: '这条帖子' })
}

function openCommentReport(comment: Comment): void {
  openReport({ targetType: 'COMMENT', targetId: comment.id, label: '这条评论' })
}

async function handleReportSubmit(reasonCode: ReportReason, detail: string): Promise<void> {
  if (await submitReport(reasonCode, detail)) {
    pushToast('举报已提交，我们会尽快核实', 'info')
  }
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

async function handleAppeal(): Promise<void> {
  const reason = appealReason.value.trim()
  if (!reason) {
    return
  }
  if (await submitAppeal(reason)) {
    pushToast('申诉已提交，将由另一位管理员复审', 'info')
    appealReason.value = ''
  }
}
</script>

<template>
  <section class="ql-shell py-8 max-lg:py-5">
    <RouterLink class="text-xs text-muted transition hover:text-primary-deep" to="/">
      ← 返回首页
    </RouterLink>

    <p v-if="loading" class="mt-6 text-center text-sm text-muted">加载中…</p>

    <!-- 失败态只在「确实没有内容可显示」时出现：若已有正文、只是重新加载失败，
         继续显示已有内容（错误已由拦截器 toast），不要用错误屏把内容盖掉。
         下面的文案是状态说明，不是请求错误信息本身，两者不重复。 -->
    <div
      v-else-if="failed && !post"
      class="mt-6 rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">内容没能加载出来</p>
      <BaseButton class="mt-4" variant="ghost" size="sm" @click="load()">重试</BaseButton>
    </div>

    <template v-else-if="post">
      <div class="mt-3 grid grid-cols-[minmax(0,1fr)_320px] gap-10 max-lg:(grid-cols-1 gap-6)">
        <div class="min-w-0 self-start">
          <BaseCard>
            <div class="flex items-center gap-2">
              <span class="ql-badge bg-primary-soft text-primary-deep">{{ post.topicName }}</span>
              <span class="truncate text-xs text-muted">{{ post.anonymousCode }}</span>
              <span v-if="post.mine" class="ql-badge shrink-0 bg-primary-tint text-primary-deep">
                我
              </span>
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

            <!--
              下架：他人已看不到本页，只有作者能打开，故这里是作者唯一的申诉入口。
              每帖限一次、限下架后 7 日内，越界由后端回 409 并由拦截器提示。
            -->
            <div
              v-if="post.mine && post.takenDown"
              class="mt-3 rounded-lg bg-danger-soft px-3 py-2.5"
            >
              <p class="text-xs font-medium text-danger-deep">内容已下架，他人不可见</p>
              <p v-if="reviewReasonText(post.reviewReason)" class="mt-1 text-xs text-danger-deep">
                处置原因：{{ reviewReasonText(post.reviewReason) }}
              </p>

              <p v-if="appealed" class="mt-2 text-xs text-danger-deep">
                申诉已提交，将由另一位管理员复审
              </p>
              <template v-else>
                <BaseTextarea
                  v-model="appealReason"
                  class="mt-2"
                  :rows="3"
                  :maxlength="500"
                  placeholder="说明申诉理由，最多 500 字"
                />
                <div class="mt-2 flex justify-end">
                  <BaseButton
                    size="sm"
                    :loading="appealSubmitting"
                    :disabled="!appealReason.trim()"
                    @click="handleAppeal"
                  >
                    提交申诉
                  </BaseButton>
                </div>
              </template>
            </div>

            <!-- 驳回：内容从未上线，没有申诉入口，只有作者看得到本页 -->
            <p
              v-else-if="post.mine && post.reviewReason"
              class="mt-3 rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger-deep"
            >
              审核未通过：{{ post.reviewReason }}
            </p>

            <div class="mt-3">
              <p
                v-if="contactText"
                class="rounded-lg bg-primary-tint px-3 py-2 text-xs text-primary-deep"
              >
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
              <button
                v-if="reportAllowed && !post.mine"
                type="button"
                class="ml-auto cursor-pointer text-xs text-muted transition hover:text-danger"
                @click="openPostReport"
              >
                举报
              </button>
            </div>
          </BaseCard>

          <BaseCard class="mt-4">
            <h2 class="text-sm font-semibold text-ink">评论</h2>

            <ul v-if="comments.length" class="mt-1 divide-y divide-line">
              <CommentItem
                v-for="comment in comments"
                :key="comment.id"
                :comment="comment"
                :reportable="reportAllowed"
                @toggle-like="toggleCommentLike"
                @reply="startReply"
                @delete="handleDeleteComment"
                @report="openCommentReport"
              />
            </ul>
            <p v-else class="mt-3 text-xs text-muted">还没有评论，说点什么吧</p>

            <form v-if="userStore.isAuthenticated" class="mt-4" @submit.prevent="handleComment">
              <div v-if="replyTo" class="mb-2 flex items-center gap-2 text-xs text-muted">
                <span>正在回复 {{ replyTo.anonymousCode }}</span>
                <button type="button" class="cursor-pointer text-primary-deep" @click="replyTo = null">
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
        </div>

        <aside class="sticky top-24 flex flex-col gap-4 self-start max-lg:hidden">
          <div class="ql-card p-4">
            <p class="text-sm font-semibold text-ink">话题规则</p>
            <p class="mt-1 text-xs font-medium text-ink">{{ post.topicName }}</p>
            <p class="mt-2 text-xs leading-relaxed text-muted">{{ post.topicRules }}</p>
          </div>

          <div class="ql-card p-4">
            <p class="text-sm font-semibold text-ink">
              {{ userStore.isAuthenticated ? '说说今天校园里的新鲜事' : '认证后即可匿名发帖' }}
            </p>
            <p class="mt-1 text-xs text-muted">
              {{
                userStore.isAuthenticated
                  ? '主帖先审后发，通过后才会出现在首页'
                  : '发帖、评论与点赞需校园邮箱认证'
              }}
            </p>
            <RouterLink
              class="ql-btn ql-btn-sm mt-3 bg-primary-deep text-white shadow-sm hover:bg-primary-strong"
              :to="userStore.isAuthenticated ? '/publish' : '/login'"
            >
              {{ userStore.isAuthenticated ? '发布新帖' : '去认证' }}
            </RouterLink>
          </div>
        </aside>
      </div>

      <ReportDialog
        :open="reportTarget !== null"
        :label="reportTarget?.label ?? ''"
        :submitting="reportSubmitting"
        @close="closeReport"
        @submit="handleReportSubmit"
      />
    </template>
  </section>
</template>
