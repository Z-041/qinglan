<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseCard from '@shared/components/base/BaseCard.vue'
import BaseInput from '@shared/components/base/BaseInput.vue'
import FormField from '@shared/components/base/FormField.vue'
import { useAuth } from '@shared/composables/useAuth'
import { useCodeCooldown } from '@shared/composables/useCodeCooldown'
import { pushToast } from '@shared/composables/useToast'
import { useUserStore } from '@shared/stores/user'
import { isAdminRole } from '@shared/types/user'

type Step = 'email' | 'code' | 'student'

/** 与后端 qinglan.auth.code-ttl 默认值保持一致，仅用于文案提示 */
const CODE_TTL_MINUTES = 5

/** 与后端学号规则一致：4-24 位字母或数字 */
const STUDENT_ID_PATTERN = /^[A-Za-z0-9]{4,24}$/

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { sending, verifying, binding, devCode, requestCode, verify, bind } = useAuth()
const { remaining: resendRemaining, start: startResendCooldown } = useCodeCooldown()

/** 单卡内联的三态：填邮箱 → 填验证码 → （新用户）补学号。不再有步骤条 */
const step = ref<Step>('email')
const email = ref('')
const code = ref('')
const studentId = ref('')

const submitting = computed(() => sending.value || verifying.value || binding.value)
/** 学号只在客户端做与后端一致的格式预校验（真正拦截在服务端） */
const studentIdValid = computed(() => STUDENT_ID_PATTERN.test(studentId.value.trim()))

/**
 * 执行认证动作，失败返回 null。
 * 错误提示已由 http 拦截器统一弹窗（AGENTS.md 第七章），这里只关心是否成功以决定是否推进。
 */
async function attempt<T>(action: () => Promise<T>): Promise<T | null> {
  try {
    return await action()
  } catch {
    return null
  }
}

/**
 * 认证成功后的统一收尾。
 *
 * 会话只有一份（一个浏览器 = 一个账号 = 一份会话，见 AGENTS.md 第七章），管理员账号
 * 与学生账号走的是同一个登录流程，因此这里不拒绝管理员，只**明确告知当前身份**——
 * 否则管理员从前台登录后，导航栏那个角色徽标很容易被忽略，误以为自己在用学生身份。
 */
function afterAuthenticated(): void {
  if (isAdminRole(userStore.profile?.role)) {
    pushToast('当前为管理员账号，可访问管理后台', 'info')
  }
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  void router.replace(redirect)
}

async function handleSendCode(): Promise<void> {
  if (resendRemaining.value > 0) {
    return
  }
  if ((await attempt(() => requestCode(email.value.trim()))) === null) {
    return
  }
  code.value = ''
  step.value = 'code'
  // 发送成功才开始冷却：失败时用户应能立刻改邮箱重试
  startResendCooldown()
}

async function handleVerify(): Promise<void> {
  const result = await attempt(() => verify(email.value.trim(), code.value.trim()))
  if (result === null) {
    return
  }
  if (result.needsStudentBinding) {
    step.value = 'student'
    return
  }
  afterAuthenticated()
}

async function handleBind(): Promise<void> {
  if (!studentIdValid.value) {
    return
  }
  if ((await attempt(() => bind(studentId.value.trim()))) === null) {
    return
  }
  afterAuthenticated()
}

/** 单表单：按当前步决定提交动作，避免多表单并存带来的歧义 */
function handleSubmit(): void {
  if (step.value === 'email') {
    void handleSendCode()
    return
  }
  if (step.value === 'code') {
    void handleVerify()
    return
  }
  void handleBind()
}

/** 回到邮箱步改邮箱：保留已填邮箱，清掉上一轮的验证码 */
function changeEmail(): void {
  step.value = 'email'
  code.value = ''
}
</script>

<template>
  <BaseCard class="mx-auto mt-10 w-full max-w-[440px] max-sm:mt-4">
    <div class="flex items-center gap-3">
      <span class="inline-flex size-10 shrink-0 items-center justify-center rounded-md bg-primary-soft text-primary">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          class="size-5"
          aria-hidden="true"
        >
          <rect x="2.5" y="4.5" width="19" height="15" rx="3" />
          <path d="m3 7 8.2 6a1.5 1.5 0 0 0 1.6 0L21 7" stroke-linecap="round" />
        </svg>
      </span>
      <div class="min-w-0">
        <h1 class="text-base font-semibold text-ink">校园邮箱认证</h1>
        <p class="mt-0.5 text-xs text-muted">认证后即可匿名发帖与互动</p>
      </div>
    </div>

    <form class="mt-6 flex flex-col gap-4" @submit.prevent="handleSubmit">
      <FormField
        v-if="step === 'email'"
        label="校园邮箱"
        hint="我们只会向该邮箱发送一次登录验证码"
      >
        <BaseInput
          v-model="email"
          name="email"
          type="email"
          autocomplete="email"
          placeholder="name@qinglan.edu.cn"
          :spellcheck="false"
          required
        />
      </FormField>

      <!-- 验证码步起把邮箱收成一行文字 + 更换入口，表单里只剩下一步真正要填的字段 -->
      <div
        v-else
        class="flex items-center justify-between gap-2 rounded-lg bg-primary-tint px-3 py-2 text-xs"
      >
        <span class="min-w-0 truncate text-muted">
          验证码已发送至 <span class="font-medium text-ink">{{ email }}</span>
        </span>
        <button
          type="button"
          class="shrink-0 cursor-pointer text-primary-deep transition-colors hover:text-primary-strong"
          @click="changeEmail"
        >
          更换邮箱
        </button>
      </div>

      <template v-if="step === 'code'">
        <p v-if="devCode" class="rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">
          本地联调验证码：<span class="font-semibold tracking-wide">{{ devCode }}</span>
        </p>
        <p class="text-xs text-muted">验证码有效期 {{ CODE_TTL_MINUTES }} 分钟。</p>
        <FormField label="验证码">
          <BaseInput
            v-model="code"
            name="code"
            inputmode="numeric"
            :maxlength="6"
            placeholder="如 123456"
            autocomplete="one-time-code"
            :spellcheck="false"
            required
          />
        </FormField>
        <BaseButton html-type="submit" block :loading="submitting">验证并登录</BaseButton>
        <BaseButton
          variant="ghost"
          size="sm"
          block
          :disabled="resendRemaining > 0"
          @click="handleSendCode"
        >
          {{ resendRemaining > 0 ? `请 ${resendRemaining}s 后再试` : '重新获取验证码' }}
        </BaseButton>
      </template>

      <template v-else-if="step === 'student'">
        <FormField
          label="学号"
          :hint="studentId && !studentIdValid ? '' : '4-24 位字母或数字，绑定后不可更改'"
          :error="studentId && !studentIdValid ? '学号须为 4-24 位字母或数字' : ''"
        >
          <BaseInput
            v-model="studentId"
            name="studentId"
            placeholder="请输入学号…"
            :spellcheck="false"
            :invalid="Boolean(studentId) && !studentIdValid"
            required
          />
        </FormField>
        <BaseButton html-type="submit" block :loading="submitting" :disabled="!studentIdValid">
          完成认证
        </BaseButton>
      </template>

      <BaseButton
        v-else
        html-type="submit"
        block
        :loading="submitting"
        :disabled="resendRemaining > 0"
      >
        {{ resendRemaining > 0 ? `请 ${resendRemaining}s 后再试` : '获取验证码' }}
      </BaseButton>
    </form>
  </BaseCard>
</template>
