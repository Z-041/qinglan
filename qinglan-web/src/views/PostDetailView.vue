<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseCard from '@shared/components/base/BaseCard.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import StatePanel from '@shared/components/StatePanel.vue'
import CommentComposer from '@/components/CommentComposer.vue'
import CommentItem from '@/components/CommentItem.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PostDetailCard from '@/components/PostDetailCard.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import SidebarCard from '@/components/SidebarCard.vue'
import TwoColumnLayout from '@/components/TwoColumnLayout.vue'
import { usePostDetail } from '@/composables/usePostDetail'
import { useReport } from '@/composables/useReport'
import { useToast } from '@shared/composables/useToast'
import { useUserStore } from '@shared/stores/user'
import type { Comment } from '@/types/comment'
import type { ReportReason } from '@shared/types/review-common'

/**
 * 帖子详情页：只做组合——主帖卡 / 评论列表 / 评论输入 / 举报弹窗 / 删除确认各自成件，
 * 页面持有跨区块的状态（评论草稿、回复对象、待删 id）与请求编排。
 */
const route = useRoute()
const userStore = useUserStore()
const { pushToast } = useToast()

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

/** 待删除的评论 id。非空即弹出二次确认——不用 window.confirm（原生框阻塞主线程、样式不可控） */
const pendingDeleteId = ref<number | null>(null)

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

async function handleAppeal(reason: string): Promise<void> {
  if (await submitAppeal(reason)) {
    pushToast('申诉已提交，将由另一位管理员复审', 'info')
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
        <TwoColumnLayout class="mt-3">
          <PostDetailCard
            :post="post"
            :authenticated="userStore.isAuthenticated"
            :report-state="reportState"
            :appealed="appealed"
            :appeal-submitting="appealSubmitting"
            @toggle-like="toggleLike()"
            @report="openPostReport"
            @appeal="handleAppeal"
          />

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

            <CommentComposer
              v-model="commentText"
              :reply-to="replyTo"
              :authenticated="userStore.isAuthenticated"
              :submitting="submitting"
              @submit="handleComment"
              @cancel-reply="replyTo = null"
            />
          </BaseCard>

          <template #aside>
            <SidebarCard title="话题规则">
              <p class="mt-1 text-xs font-medium text-ink">{{ post.topicName }}</p>
              <p class="mt-2 text-xs leading-relaxed text-muted">{{ post.topicRules }}</p>
            </SidebarCard>

            <SidebarCard
              :title="userStore.isAuthenticated ? '说说今天校园里的新鲜事' : '认证后即可匿名发帖'"
            >
              <p class="mt-1 text-xs text-muted">
                {{
                  userStore.isAuthenticated
                    ? '主帖先审后发，通过后才会出现在首页'
                    : '发帖、评论与点赞需校园邮箱认证'
                }}
              </p>
              <BaseButton
                as="RouterLink"
                size="sm"
                class="mt-3"
                :to="userStore.isAuthenticated ? '/publish' : '/login'"
              >
                {{ userStore.isAuthenticated ? '发布新帖' : '去认证' }}
              </BaseButton>
            </SidebarCard>
          </template>
        </TwoColumnLayout>

        <ReportDialog
          :open="reportTarget !== null"
          :label="reportTarget?.label ?? ''"
          :submitting="reportSubmitting"
          @close="closeReport"
          @submit="handleReportSubmit"
        />

        <!-- 删除评论的二次确认：自绘弹层，与 ReportDialog 共用 ModalShell 外壳 -->
        <ConfirmDialog
          :open="pendingDeleteId !== null"
          label="删除评论"
          title="确定删除这条评论吗？"
          message="删除后不可恢复；若是一级评论，其回复会一并删除。"
          confirm-text="删除"
          danger
          @close="pendingDeleteId = null"
          @confirm="confirmDelete"
        />
      </template>
    </StatePanel>
  </section>
</template>
