<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { fetchAuditLogs } from '@/api/admin'
import BaseButton from '@shared/components/base/BaseButton.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminAuditLogItem, AuditTargetType } from '@/types/admin'

/**
 * 审计动作码 → 中文。与后端 common/audit/AuditAction 一一对应。
 * 未知码退化为原码展示（版本不同步时宁可显示码，也不要渲染出空）。
 */
const ACTION_LABELS: Record<string, string> = {
  BAN: '封禁用户',
  UNBAN: '解封用户',
  CREDIT_ADJUST: '调整信用分',
  POST_APPROVE: '放行帖子',
  POST_REJECT: '驳回帖子',
  POST_TAKEDOWN: '下架帖子',
  POST_AUTO_TAKEDOWN: '自动下架（举报达阈值）',
  POST_RESTORE: '恢复帖子可见',
  POST_HIDE_CONTACT: '隐藏联系方式',
  POST_SHOW_CONTACT: '恢复联系方式',
  COMMENT_REMOVE: '移除评论',
  REPORT_DISMISS: '驳回举报',
  REPORT_UPHOLD: '维持下架',
  APPEAL_REVIEW: '复审申诉',
  ROLE_GRANT: '下放管理权限',
  ROLE_REVOKE: '收回管理权限',
  ANNOUNCEMENT_CREATE: '创建公告草稿',
  ANNOUNCEMENT_PUBLISH: '发布公告',
  ANNOUNCEMENT_REVOKE: '撤回公告',
  /** 查看联系方式：由普通用户浏览触发，量级最大，排在最后 */
  CONTACT_VIEW: '查看联系方式',
}

const TARGET_TYPE_LABELS: Record<AuditTargetType, string> = {
  USER: '用户',
  POST: '帖子',
  COMMENT: '评论',
  ANNOUNCEMENT: '公告',
}

const { reviewReasonText } = useUserLabels()

const items = ref<AdminAuditLogItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const failed = ref(false)

const actionFilter = ref('')
const fromFilter = ref('')
const toFilter = ref('')

let cursor: string | null = null
/** 请求序号：筛选条件改动会触发 refresh，与在飞的 loadMore 可能交叉，先发的不一定先到 */
let requestSeq = 0

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
}

function toInstant(value: string): string | null {
  // datetime-local 是本地时间，转成 UTC 的 ISO 串交给后端（与公告表单同口径）
  return value ? new Date(value).toISOString() : null
}

function applyPage(page: { items: AdminAuditLogItem[]; nextCursor: string | null }, append: boolean): void {
  items.value = append ? [...items.value, ...page.items] : page.items
  cursor = page.nextCursor
  hasMore.value = page.nextCursor !== null
}

async function refresh(): Promise<void> {
  const seq = ++requestSeq
  loading.value = true
  failed.value = false
  items.value = []
  cursor = null
  hasMore.value = false
  try {
    const page = await fetchAuditLogs({
      action: actionFilter.value || null,
      from: toInstant(fromFilter.value),
      to: toInstant(toFilter.value),
    })
    if (seq !== requestSeq) {
      return
    }
    applyPage(page, false)
  } catch {
    // 错误已由拦截器提示；这里只把「加载失败」与「暂无记录」分成两屏
    if (seq === requestSeq) {
      failed.value = true
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

async function loadMore(): Promise<void> {
  if (!hasMore.value || loading.value || loadingMore.value) {
    return
  }
  const seq = requestSeq
  loadingMore.value = true
  try {
    const page = await fetchAuditLogs({
      cursor,
      action: actionFilter.value || null,
      from: toInstant(fromFilter.value),
      to: toInstant(toFilter.value),
    })
    if (seq !== requestSeq) {
      return
    }
    applyPage(page, true)
  } catch {
    // 错误已由拦截器提示
  } finally {
    loadingMore.value = false
  }
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">审计日志</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <p class="text-sm text-muted">
      记录全部治理动作，只增不改删；操作人只以 ID 出现，不含学号与邮箱明文。
    </p>

    <section class="ql-card overflow-hidden">
      <div class="flex flex-wrap items-center gap-2 border-b border-line px-3 py-3">
        <el-select v-model="actionFilter" class="w-[200px]" placeholder="全部动作" clearable>
          <el-option
            v-for="(label, action) in ACTION_LABELS"
            :key="action"
            :label="label"
            :value="action"
          />
        </el-select>
        <el-input v-model="fromFilter" type="datetime-local" class="w-[210px]" placeholder="起始时间" />
        <span class="text-xs text-muted">至</span>
        <el-input v-model="toFilter" type="datetime-local" class="w-[210px]" placeholder="结束时间" />
        <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">筛选</BaseButton>
      </div>

      <table class="w-full border-collapse text-sm">
        <thead>
          <tr class="border-b border-line text-left text-xs text-muted">
            <th class="px-3 py-2.5 font-medium">时间</th>
            <th class="px-3 py-2.5 font-medium">动作</th>
            <th class="px-3 py-2.5 font-medium">目标</th>
            <th class="px-3 py-2.5 font-medium">操作人</th>
            <th class="px-3 py-2.5 font-medium">理由</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id" class="border-b border-line last:border-0">
            <td class="px-3 py-3 whitespace-nowrap">{{ new Date(row.createdAt).toLocaleString() }}</td>
            <td class="px-3 py-3">{{ actionLabel(row.action) }}</td>
            <td class="px-3 py-3">{{ TARGET_TYPE_LABELS[row.targetType] }} #{{ row.targetId }}</td>
            <td class="px-3 py-3">
              <!-- 0 是系统自动处置的哨兵值（后端 AuditLogService.SYSTEM_OPERATOR_ID） -->
              {{ row.operatorId === 0 ? '系统' : `#${row.operatorId}` }}
            </td>
            <td class="max-w-[360px] truncate px-3 py-3" :title="reviewReasonText(row.reason) ?? ''">
              {{ reviewReasonText(row.reason) ?? '—' }}
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="5" class="px-3 py-10 text-center text-sm text-muted">
              {{ loading ? '加载中…' : failed ? '加载失败，请点击「刷新」重试' : '暂无审计记录' }}
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">加载更多</BaseButton>
      </div>
    </section>
  </div>
</template>
