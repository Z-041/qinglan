<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { changeUserRole, fetchCommittee } from '@/api/admin'
import DataTable from '@/components/admin/DataTable.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseCard from '@shared/components/base/BaseCard.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
import { pushToast } from '@shared/composables/useToast'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { useSubmit } from '@shared/composables/useSubmit'
import { useReasonPrompt } from '@/composables/useReasonPrompt'
import {
  ASSIGNABLE_ROLE_OPTIONS,
  isCommitteeRole,
  isRoleRevocable,
  MAX_COMMITTEE_SIZE,
} from '@/composables/rolePolicy'
import type { AssignableRole } from '@/types/admin'
import type { UserProfile } from '@shared/types/user'

const { roleLabel, statusLabel } = useUserLabels()

const members = ref<UserProfile[]>([])
const loading = ref(false)
const failed = ref(false)
const { submitting, run } = useSubmit()
/** 权限变更必须留下理由：它是审计里唯一能回答「为什么是他」的字段（弹窗封装见 useReasonPrompt） */
const { promptReason } = useReasonPrompt()

const targetUserId = ref('')
const targetRole = ref<AssignableRole>('REVIEWER')

/** 在册人数由后端名单长度决定，不在前端另算——两边各算一次必然漂移 */
const used = computed(() => members.value.filter((member) => isCommitteeRole(member.role)).length)
const full = computed(() => used.value >= MAX_COMMITTEE_SIZE)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    members.value = await fetchCommittee()
  } catch {
    // 错误已由拦截器提示；这里只区分「加载失败」与「暂无成员」
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

async function submitChange(userId: number, role: AssignableRole, title: string): Promise<boolean> {
  const reason = await promptReason(title, '理由会写入审计日志，事后可追溯')
  if (reason === null) {
    return false
  }
  // 失败（名额已满 / 不可动超管 / 不能改自己）已由拦截器提示
  return run(async () => {
    await changeUserRole(userId, { role, reason })
    pushToast(isCommitteeRole(role) ? '已下放权限' : '已收回管理权限', 'info')
    await load()
  })
}

async function grant(): Promise<void> {
  const parsed = Number(targetUserId.value.trim())
  if (!Number.isInteger(parsed) || parsed <= 0) {
    pushToast('请填写有效的用户 ID', 'error')
    return
  }
  if (await submitChange(parsed, targetRole.value, '下放管理权限')) {
    targetUserId.value = ''
  }
}

async function revoke(member: UserProfile): Promise<void> {
  await submitChange(member.id, 'STUDENT', `收回权限（userId=${member.id}）`)
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <PageHeader title="权限管理" :refreshing="loading" @refresh="load" />

    <p class="text-sm text-muted">
      管理员与学生评议员共同组成处置表决委员会，合计上限 {{ MAX_COMMITTEE_SIZE }} 人；
      超级管理员唯一且不可被下放或收回。
    </p>

    <BaseCard>
      <h2 class="text-sm font-medium text-muted">下放管理权限</h2>
      <p class="mt-1 text-xs text-muted">
        <!-- 不变量 #3：后台看不到学号与邮箱，因此只能按用户 ID 操作 -->
        出于隐私要求后台看不到学号与邮箱，请填写目标用户的 ID（可由本人提供）。
      </p>
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <el-input
          v-model="targetUserId"
          class="w-[160px]"
          placeholder="用户 ID"
          inputmode="numeric"
          aria-label="目标用户 ID"
        />
        <el-select v-model="targetRole" class="w-[160px]" aria-label="要授予的角色">
          <el-option
            v-for="option in ASSIGNABLE_ROLE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <BaseButton variant="primary" size="sm" :disabled="submitting || full" @click="grant">
          授予
        </BaseButton>
        <span v-if="full" class="text-xs text-danger-deep">
          名额已满（{{ used }} / {{ MAX_COMMITTEE_SIZE }}），请先收回其他成员的权限
        </span>
      </div>
    </BaseCard>

    <section class="ql-card overflow-clip">
      <div class="flex items-center justify-between border-b border-line px-3 py-2.5">
        <span class="text-sm font-medium text-ink">委员会成员</span>
        <span class="text-xs text-muted">{{ used }} / {{ MAX_COMMITTEE_SIZE }}</span>
      </div>
      <DataTable
        :items="members"
        :colspan="6"
        :loading="loading"
        :failed="failed"
        empty-text="暂无成员"
      >
        <template #head>
          <th class="px-3 py-2.5 font-medium">用户 ID</th>
          <th class="px-3 py-2.5 font-medium">角色</th>
          <th class="px-3 py-2.5 font-medium">状态</th>
          <th class="px-3 py-2.5 font-medium">信用分</th>
          <th class="px-3 py-2.5 font-medium">加入时间</th>
          <th class="px-3 py-2.5 font-medium">操作</th>
        </template>

        <tr v-for="member in members" :key="member.id" class="border-b border-line last:border-0">
          <td class="px-3 py-3">{{ member.id }}</td>
          <td class="px-3 py-3">{{ roleLabel(member.role) }}</td>
          <td class="px-3 py-3">{{ statusLabel(member.status) }}</td>
          <td class="px-3 py-3">{{ member.creditScore }}</td>
          <td class="px-3 py-3">
            <RelativeTime :iso="member.createdAt" />
          </td>
          <td class="px-3 py-3">
            <!-- 超管不可被收回：后端也会拒（唯一超管是后台的最后一道保险） -->
            <BaseButton
              v-if="isRoleRevocable(member.role)"
              variant="ghost-danger"
              size="xs"
              :disabled="submitting"
              @click="revoke(member)"
            >
              收回权限
            </BaseButton>
            <span v-else class="text-xs text-muted">不可变更</span>
          </td>
        </tr>
      </DataTable>
    </section>
  </div>
</template>
