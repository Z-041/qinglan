<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  createAnnouncement,
  fetchAdminAnnouncements,
  publishAnnouncement,
  revokeAnnouncement,
} from '@/api/admin'
import AnnouncementFormDialog from '@/components/admin/AnnouncementFormDialog.vue'
import AnnouncementTable from '@/components/admin/AnnouncementTable.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import { useCursorList } from '@shared/composables/useCursorList'
import { useSubmit } from '@shared/composables/useSubmit'
import { useConfirm } from '@/composables/useConfirm'
import { usePermissions } from '@/composables/usePermissions'
import type { AdminAnnouncementItem, CreateAnnouncementPayload } from '@/types/admin'

const { can } = usePermissions()

/**
 * 发布 / 撤回仅超管（P4 决策 D8，P5 权限点 ANNOUNCEMENT_MANAGE）。
 *
 * 判权限点而不是判角色：角色差异只体现在后端下发的权限点上，
 * 页面里不再出现 `role === 'SUPER_ADMIN'` 这类硬编码（P5 设计 9.1）。
 */
const canOperate = computed(() => can('ANNOUNCEMENT_MANAGE'))

/** 提交态统一走 shared/useSubmit；成功才关弹窗，失败保持打开（错误由拦截器提示） */
const { submitting, run } = useSubmit()
const { confirm } = useConfirm()
const formOpen = ref(false)

/**
 * 列表状态机（清空再请求、请求序号防竞态、四态）收在 shared useCursorList——
 * 与其余队列页口径一致。发布/撤回后的 refresh 与用户点「刷新」可能同时在飞，
 * 先发的不一定先到，序号防竞态由该 composable 统一负责。
 */
const { items, loading, loadingMore, hasMore, failed, refresh, loadMore } =
  useCursorList<AdminAnnouncementItem>({
    fetchPage: (cursor) => fetchAdminAnnouncements(cursor),
  })

onMounted(() => {
  void refresh()
})

async function handleCreate(payload: CreateAnnouncementPayload): Promise<void> {
  await run(async () => {
    await createAnnouncement(payload)
    formOpen.value = false
    await refresh()
  })
}

/**
 * 发布是不可逆的全员触达：先二次确认，明确它等于「面向全员的强制弹窗」。
 * 用户取消返回 false，静默返回。
 */
async function handlePublish(row: AdminAnnouncementItem): Promise<void> {
  const ok = await confirm(
    `「${row.title}」发布后即为面向全员的强制弹窗，未读用户进入页面时必须读完才能继续。确认发布？`,
    '发布公告',
    '确认发布',
  )
  if (!ok) {
    return
  }
  // 失败错误已由拦截器提示（QL-409-008 状态不允许 / QL-429-004 当日发布超上限）
  await run(async () => {
    await publishAnnouncement(row.id)
    await refresh()
  })
}

async function handleRevoke(row: AdminAnnouncementItem): Promise<void> {
  const ok = await confirm(
    `撤回「${row.title}」后用户将不再被强制阅读该公告，已读记录保留。确认撤回？`,
    '撤回公告',
    '确认撤回',
  )
  if (!ok) {
    return
  }
  await run(async () => {
    await revokeAnnouncement(row.id)
    await refresh()
  })
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <PageHeader :refreshing="loading" title="公告管理" @refresh="refresh">
      <template #extra>
        <span v-if="!canOperate" class="text-xs text-muted">发布与撤回仅超级管理员可操作</span>
        <BaseButton size="sm" @click="formOpen = true">新建公告</BaseButton>
      </template>
    </PageHeader>

    <section class="ql-card overflow-clip">
      <AnnouncementTable
        :items="items"
        :loading="loading"
        :failed="failed"
        :can-operate="canOperate"
        :submitting="submitting"
        @publish="handlePublish"
        @revoke="handleRevoke"
      />

      <LoadMoreButton variant="table" :has-more="hasMore" :loading="loadingMore" @load="loadMore" />
    </section>

    <AnnouncementFormDialog
      :open="formOpen"
      :submitting="submitting"
      @submit="handleCreate"
      @close="formOpen = false"
    />
  </div>
</template>
