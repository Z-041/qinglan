<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import FormField from '@/components/base/FormField.vue'
import { useAuth } from '@/composables/useAuth'
import { pushToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'
import type { UserRole } from '@/types/user'

/** 与后端 qinglan.auth.code-ttl 默认值保持一致，仅用于文案提示 */
const CODE_TTL_MINUTES = 5

/** 管理后台的准入角色（spec D3：REVIEWER 本轮不放行） */
const ADMIN_ROLES: UserRole[] = ['ADMIN', 'SUPER_ADMIN']

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { sending, verifying, devCode, requestCode, verify } = useAuth()

const step = ref<'email' | 'code'>('email')
const email = ref('')
const code = ref('')

const submitting = computed(() => sending.value || verifying.value)

/**
 * 执行认证动作，失败返回 null。
 * 错误提示已由 http 拦截器统一弹窗（AGENTS.md 第七章），这里只判断是否成功以决定推进。
 */
async function attempt<T>(action: () => Promise<T>): Promise<T | null> {
  try {
    return await action()
  } catch {
    return null
  }
}

function targetPath(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/admin') ? redirect : '/admin/dashboard'
}

async function handleSendCode(): Promise<void> {
  if ((await attempt(() => requestCode(email.value.trim()))) === null) {
    return
  }
  code.value = ''
  step.value = 'code'
}

async function handleVerify(): Promise<void> {
  const result = await attempt(() => verify(email.value.trim(), code.value.trim()))
  if (result === null) {
    return
  }
  if (result.needsStudentBinding) {
    userStore.signOut()
    pushToast('该邮箱尚未绑定学号，无法登录管理后台', 'error')
    return
  }
  if (!userStore.profile || !ADMIN_ROLES.includes(userStore.profile.role)) {
    // 学生账号可能被用来试后台：必须清干净登录态，不能把令牌留在本地
    userStore.signOut()
    pushToast('该账号没有管理后台权限', 'error')
    return
  }
  void router.replace(targetPath())
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-canvas px-6">
    <BaseCard class="w-full max-w-[440px]">
      <div class="flex items-center gap-3">
        <span class="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="size-5" aria-hidden="true">
            <path d="M12 3 4 6.5v5c0 4.4 3.2 8.4 8 9.5 4.8-1.1 8-5.1 8-9.5v-5Z" stroke-linejoin="round" />
            <path d="m9 12 2 2 4-4" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </span>
        <div class="min-w-0">
          <h1 class="text-base font-semibold text-ink">青栏管理后台</h1>
          <p class="mt-0.5 text-xs text-muted">仅限管理员账号（ADMIN / SUPER_ADMIN）</p>
        </div>
      </div>

      <form v-if="step === 'email'" class="mt-6 flex flex-col gap-4" @submit.prevent="handleSendCode">
        <FormField label="校园邮箱" hint="使用管理员账号绑定的校园邮箱">
          <BaseInput v-model="email" type="email" autocomplete="email" placeholder="name@qinglan.edu.cn" required />
        </FormField>
        <BaseButton html-type="submit" block :loading="submitting">获取验证码</BaseButton>
      </form>

      <form v-else class="mt-6 flex flex-col gap-4" @submit.prevent="handleVerify">
        <p class="rounded-lg bg-primary-tint px-3 py-2 text-xs leading-relaxed text-muted">
          验证码已发送至 <span class="font-medium text-ink">{{ email }}</span
          >，有效期 {{ CODE_TTL_MINUTES }} 分钟。
        </p>
        <p v-if="devCode" class="rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">
          本地联调验证码：<span class="font-semibold tracking-wide">{{ devCode }}</span>
        </p>
        <FormField label="验证码">
          <BaseInput v-model="code" inputmode="numeric" :maxlength="6" placeholder="6 位数字" required />
        </FormField>
        <BaseButton html-type="submit" block :loading="submitting">登录</BaseButton>
        <BaseButton variant="ghost" size="sm" block @click="step = 'email'">更换邮箱</BaseButton>
      </form>
    </BaseCard>
  </div>
</template>
