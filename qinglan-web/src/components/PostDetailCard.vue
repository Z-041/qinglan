<script setup lang="ts">
import { computed } from 'vue'

import BaseCard from '@shared/components/base/BaseCard.vue'
import AlertBox from '@/components/AlertBox.vue'
import AppealPanel from '@/components/AppealPanel.vue'
import LikeButton from '@/components/LikeButton.vue'
import PostImageGrid from '@/components/PostImageGrid.vue'
import PostMetaRow from '@/components/PostMetaRow.vue'
import TextButton from '@/components/TextButton.vue'
import type { PostDetail } from '@/types/post'
import type { MyReportState, ReportStatus } from '@/types/report'

/**
 * 详情页主帖卡片：元信息 → 正文 → 配图 → 下架/驳回提示（含作者申诉）→ 联系方式 → 互动条。
 *
 * 联系方式可见性完全由服务端裁决（不变量 #2）：`contactVisible` 为真时后端才回填 `contact`，
 * 即 `contact` 非空 ⟺ 可见；被处置隐藏时对非作者同样返回 null，与「作者根本没填」在前端
 * 不可区分——故已认证时不能武断地说「作者未提供」，那会是假信息。
 *
 * 纯展示 + 上抛事件（点赞 / 举报 / 申诉），自身不取用户态、不发请求。
 */
const props = withDefaults(
  defineProps<{
    post: PostDetail
    authenticated: boolean
    /** 我对该帖的举报状态；未登录或未举报为 null */
    reportState?: MyReportState | null
    appealed?: boolean
    appealSubmitting?: boolean
  }>(),
  { reportState: null, appealed: false, appealSubmitting: false },
)

const emit = defineEmits<{
  'toggle-like': []
  report: []
  appeal: [reason: string]
}>()

const contactText = computed(() => props.post.contact ?? '')

/** 我已举报过该帖。体验层用它把入口置灰、提前挡掉重复举报；真正的防线是后端不变量 #4（409） */
const reported = computed(() => props.reportState?.reported === true)

/** 举报进度文案：PENDING 处理中 / UPHELD 举报成立、内容已处理 / DISMISSED 未获支持 */
const REPORT_STATUS_TEXT: Record<ReportStatus, string> = {
  PENDING: '处理中',
  UPHELD: '举报成立，内容已处理',
  DISMISSED: '未获支持',
}
const reportStatusText = computed(() => {
  const status = props.reportState?.status
  return status ? REPORT_STATUS_TEXT[status] : ''
})
</script>

<template>
  <BaseCard>
    <PostMetaRow
      :topic-name="props.post.topicName"
      :mine="props.post.mine"
      :anonymous-code="props.post.anonymousCode"
      :created-at="props.post.createdAt"
    />

    <p class="mt-3 text-[17px] leading-[1.75] break-words whitespace-pre-wrap text-ink">
      {{ props.post.content }}
    </p>

    <PostImageGrid class="mt-3" variant="detail" :urls="props.post.images" />

    <!--
      下架：他人已看不到本页，只有作者能打开，故这里是作者唯一的申诉入口。
      每帖限一次、限下架后 7 日内，越界由后端回 409 并由拦截器提示。
    -->
    <AppealPanel
      v-if="props.post.mine && props.post.takenDown"
      :review-reason="props.post.reviewReason"
      :appealed="props.appealed"
      :submitting="props.appealSubmitting"
      @submit="emit('appeal', $event)"
    />

    <!-- 驳回：内容从未上线，没有申诉入口，只有作者看得到本页 -->
    <AlertBox v-else-if="props.post.mine && props.post.reviewReason" class="mt-3">
      审核未通过：{{ props.post.reviewReason }}
    </AlertBox>

    <div class="mt-3">
      <AlertBox v-if="contactText" tone="primary">
        联系方式：<span class="font-medium">{{ contactText }}</span>
      </AlertBox>
      <p v-else-if="!props.authenticated" class="text-xs text-muted">
        <RouterLink class="text-primary-deep" to="/login">认证后</RouterLink>可查看联系方式
      </p>
      <p v-else class="text-xs text-muted">作者未提供联系方式，或已被管理员隐藏</p>
    </div>

    <div class="mt-4 flex items-center gap-4 border-t border-line pt-3">
      <LikeButton
        :liked="props.post.liked"
        :count="props.post.likeCount"
        :label="props.post.liked ? '取消点赞' : '点赞'"
        @toggle="emit('toggle-like')"
      />
      <span class="text-xs text-muted">评论 {{ props.post.commentCount }}</span>
      <!-- 已举报时入口置灰并展示处理进度，提前挡掉重复举报（防线仍是后端 409） -->
      <div v-if="props.authenticated && !props.post.mine" class="ml-auto flex items-center gap-2">
        <span v-if="reported" class="text-xs text-muted">举报{{ reportStatusText }}</span>
        <TextButton tone="danger" :disabled="reported" @click="emit('report')">
          {{ reported ? '已举报' : '举报' }}
        </TextButton>
      </div>
    </div>
  </BaseCard>
</template>
