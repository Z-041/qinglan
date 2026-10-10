<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseCard from '@shared/components/base/BaseCard.vue'
import BaseIcon from '@shared/components/base/BaseIcon.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import StatePanel from '@shared/components/StatePanel.vue'
import CommentItem from '@/components/CommentItem.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
import { usePostDetail } from '@/composables/usePostDetail'
import { useReport } from '@/composables/useReport'
import { useToast } from '@shared/composables/useToast'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { useUserStore } from '@shared/stores/user'
import type { Comment } from '@/types/comment'
import type { ReportStatus } from '@/types/report'
import type { ReportReason } from '@shared/types/review-common'

const route = useRoute()
const userStore = useUserStore()
const { pushToast } = useToast()
const { reviewReasonText } = useUserLabels()

const postId = Number(route.params.id)
const {
  post,
  comments,
  reportState,
  loading,
  loadingMore,
  hasMore,
  failed,
  submitting,
  appealSubmitting,
  appealed,
  load,
  refreshReportState,
  loadMoreComments,
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

/** 待删除的评论 id。非空即弹出二次确认——不用 window.confirm（原生框阻塞主线程、样式不可控） */
const pendingDeleteId = ref<number | null>(null)
const deleteDialogRef = ref<HTMLElement | null>(null)

/**
 * 联系方式可见性完全由服务端裁决（不变量 #2）：`contactVisible` 为真时后端才回填 `contact`，
 * 即 `contact` 非空 ⟺ 可见。被处置隐藏（`contactVisibility=HIDDEN`）时对非作者同样返回 null，
 * 与「作者根本没填」在前端不可区分——故已认证时不能武断地说「作者未提供」，那会是假信息。
 */
const contactText = computed(() => post.value?.contact ?? '')

/** 举报入口只对已认证访问者出现：举报人取自登录态，游客点了也只会被弹去登录 */
const reportAllowed = computed(() => userStore.isAuthenticated)

/** 我已举报过该帖。体验层用它把入口置灰、提前挡掉重复举报；真正的防线是后端不变量 #4（409） */
const reported = computed(() => reportState.value?.reported === true)

/** 举报进度文案：PENDING 处理中 / UPHELD 举报成立、内容已处理 / DISMISSED 未获支持 */
const REPORT_STATUS_TEXT: Record<ReportStatus, string> = {
  PENDING: '处理中',
  UPHELD: '举报成立，内容已处理',
  DISMISSED: '未获支持',
}
const reportStatusText = computed(() => {
  const status = reportState.value?.status
  return status ? REPORT_STATUS_TEXT[status] : ''
})

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
    // 刷新举报状态：入口随即置灰并显示「处理中」，避免用户重复提交拿到 409
    await refreshReportState()
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

function requestDelete(commentId: number): void {
  pendingDeleteId.value = commentId
}

// 确认框打开时把焦点收进去，ESC 才生效、键盘用户也不会把焦点留在背后页面上
watch(pendingDeleteId, async (id) => {
  if (id === null) {
    return
  }
  await nextTick()
  deleteDialogRef.value?.focus()
})

async function confirmDelete(): Promise<void> {
  const commentId = pendingDeleteId.value
  if (commentId === null) {
    return
  }
  pendingDeleteId.value = null
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

    <!-- 失败态只在「确实没有内容可显示」时出现：若已有正文、只是重新加载失败，
         继续显示已有内容（错误已由拦截器 toast）。文案是状态说明，由 StatePanel 统一呈现 -->
    <StatePanel
      :state="loading ? 'loading' : failed && !post ? 'failed' : 'ready'"
      failed-title="内容没能加载出来"
      @retry="load()"
    >
      <template v-if="post">
      <!-- 帖子无标题字段，h1 仅用于建立标题层级（下方「评论」是 h2），视觉隐藏 -->
      <h1 class="sr-only">帖子详情</h1>
      <div class="mt-3 grid grid-cols-[minmax(0,1fr)_320px] gap-10 max-lg:(grid-cols-1 gap-6)">
        <div class="min-w-0 self-start">
          <BaseCard>
            <div class="flex items-center gap-2">
              <span class="ql-tag shrink-0 bg-primary-soft text-primary-deep">{{ post.topicName }}</span>
              <span v-if="post.mine" class="ql-tag shrink-0 bg-primary-tint text-primary-deep">
                我
              </span>
              <span class="truncate text-xs text-muted">{{ post.anonymousCode }}</span>
              <RelativeTime class="ml-auto shrink-0 text-xs text-muted" :iso="post.createdAt" />
            </div>

            <p class="mt-3 text-[17px] leading-[1.75] break-words whitespace-pre-wrap text-ink">
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
                  name="appealReason"
                  aria-label="申诉理由"
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
              <p v-else class="text-xs text-muted">作者未提供联系方式，或已被管理员隐藏</p>
            </div>

            <div class="mt-4 flex items-center gap-4 border-t border-line pt-3">
              <button
                type="button"
                class="flex cursor-pointer items-center gap-1.5 text-sm transition"
                :class="post.liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
                :aria-pressed="post.liked"
                :aria-label="post.liked ? '取消点赞' : '点赞'"
                @click="toggleLike()"
              >
                <BaseIcon name="heart" class="size-4" :filled="post.liked" />
                <span>{{ post.likeCount }}</span>
              </button>
              <span class="text-xs text-muted">评论 {{ post.commentCount }}</span>
              <!-- 已举报时入口置灰并展示处理进度，提前挡掉重复举报（防线仍是后端 409） -->
              <div v-if="reportAllowed && !post.mine" class="ml-auto flex items-center gap-2">
                <span v-if="reported" class="text-xs text-muted">举报{{ reportStatusText }}</span>
                <button
                  type="button"
                  class="cursor-pointer text-xs text-muted transition hover:text-danger disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:text-muted"
                  :disabled="reported"
                  @click="openPostReport"
                >
                  {{ reported ? '已举报' : '举报' }}
                </button>
              </div>
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
                @delete="requestDelete"
                @report="openCommentReport"
              />
            </ul>
            <p v-else class="mt-3 text-xs text-muted">还没有评论，说点什么吧</p>

            <!-- 一级评论游标分页：还有下一页时才出现，点一次追加一批；回复随所属一级评论整页带出 -->
            <LoadMoreButton
              v-if="comments.length"
              :has-more="hasMore"
              :loading="loadingMore"
              @load="loadMoreComments"
            />

            <form v-if="userStore.isAuthenticated" class="mt-4" @submit.prevent="handleComment">
              <div v-if="replyTo" class="mb-2 flex items-center gap-2 text-xs text-muted">
                <span>正在回复 {{ replyTo.anonymousCode }}</span>
                <button
                  type="button"
                  class="cursor-pointer text-primary-deep transition-colors hover:text-primary-strong"
                  @click="replyTo = null"
                >
                  取消
                </button>
              </div>
              <BaseTextarea
                v-model="commentText"
                name="comment"
                aria-label="评论内容"
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

      <!-- 删除评论的二次确认：自绘弹层，语义与行为对齐 ReportDialog（遮罩/ESC 关闭，焦点收入层内） -->
      <div
        v-if="pendingDeleteId !== null"
        ref="deleteDialogRef"
        tabindex="-1"
        class="fixed inset-0 z-overlay flex overscroll-contain items-center justify-center bg-black/40 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-label="删除评论"
        @click.self="pendingDeleteId = null"
        @keydown.escape="pendingDeleteId = null"
      >
        <div class="w-full max-w-sm rounded-xl border border-line bg-surface p-4 shadow-xl">
          <p class="text-sm font-semibold text-ink">确定删除这条评论吗？</p>
          <p class="mt-1 text-xs text-muted">删除后不可恢复；若是一级评论，其回复会一并删除。</p>
          <div class="mt-4 flex justify-end gap-2">
            <BaseButton variant="ghost" size="sm" @click="pendingDeleteId = null">取消</BaseButton>
            <BaseButton variant="danger" size="sm" @click="confirmDelete">删除</BaseButton>
          </div>
        </div>
      </div>
      </template>
    </StatePanel>
  </section>
</template>
