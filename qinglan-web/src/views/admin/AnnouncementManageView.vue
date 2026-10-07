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
import BaseButton from '@/components/base/BaseButton.vue'
import { useUserStore } from '@/stores/user'
import type { AdminAnnouncementItem, CreateAnnouncementPayload } from '@/types/admin'

const userStore = useUserStore()

/** 发布/撤回仅 SUPER_ADMIN（P4 决策 D8）；普通管理员只能建草稿 */
const canOperate = computed(() => userStore.profile?.role === 'SUPER_ADMIN')

const items = ref<AdminAnnouncementItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const submitting = ref(false)
const formOpen = ref(false)
/** 首屏是否失败过。用于把「加载失败」与「还没有公告」分成两屏 */
const failed = ref(false)

/** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
let cursor: string | null = null

function applyPage(
  page: { items: AdminAnnouncementItem[]; nextCursor: string | null },
  append: boolean,
): void {
  items.value = append ? [...items.value, ...page.items] : page.items
  cursor = page.nextCursor
  hasMore.value = page.nextCursor !== null
}

async function refresh(): Promise<void> {
  loading.value = true
  failed.value = false
  // 先清干净再请求：否则本次失败时列表与游标会留在上一次的结果集上，
  // 此后点「加载更多」就会拿「新一次 + 旧游标」去请求
  items.value = []
  cursor = null
  hasMore.value = false
  try {
    applyPage(await fetchAdminAnnouncements(null), false)
  } catch {
    // 错误已由拦截器提示；这里只记状态位，供表格区分「加载失败」与「没有公告」
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function loadMore(): Promise<void> {
  if (!hasMore.value || loading.value || loadingMore.value) {
    return
  }
  loadingMore.value = true
  try {
    applyPage(await fetchAdminAnnouncements(cursor), true)
  } catch {
    // 错误已由拦截器提示
  } finally {
    loadingMore.value = false
  }
}

onMounted(() => {
  void refresh()
})

async function handleCreate(payload: CreateAnnouncementPayload): Promise<void> {
  submitting.value = true
  try {
    await createAnnouncement(payload)
    formOpen.value = false
    await refresh()
  } catch {
    // 失败保持弹窗打开便于修正，错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

/**
 * 发布是不可逆的全员触达：先二次确认，明确它等于「面向全员的强制弹窗」。
 * 用户取消时 ElMessageBox 以 reject 表示，不是错误，静默返回。
 */
async function handlePublish(row: AdminAnnouncementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `「${row.title}」发布后即为面向全员的强制弹窗，未读用户进入页面时必须读完才能继续。确认发布？`,
      '发布公告',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await publishAnnouncement(row.id)
    await refresh()
  } catch {
    // 错误已由拦截器提示（QL-409-008 状态不允许 / QL-429-004 当日发布超上限）
  } finally {
    submitting.value = false
  }
}

async function handleRevoke(row: AdminAnnouncementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `撤回「${row.title}」后用户将不再被强制阅读该公告，已读记录保留。确认撤回？`,
      '撤回公告',
      { confirmButtonText: '确认撤回', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await revokeAnnouncement(row.id)
    await refresh()
  } catch {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">公告管理</h1>
      <div class="flex items-center gap-3">
        <span v-if="!canOperate" class="text-xs text-muted">发布与撤回仅超级管理员可操作</span>
        <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
        <BaseButton size="sm" @click="formOpen = true">新建公告</BaseButton>
      </div>
    </div>

    <section class="ql-card overflow-hidden">
      <AnnouncementTable
        :items="items"
        :loading="loading"
        :failed="failed"
        :can-operate="canOperate"
        :submitting="submitting"
        @publish="handlePublish"
        @revoke="handleRevoke"
      />

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">
          加载更多
        </BaseButton>
      </div>
    </section>

    <AnnouncementFormDialog
      :open="formOpen"
      :submitting="submitting"
      @submit="handleCreate"
      @close="formOpen = false"
    />
  </div>
</template>
