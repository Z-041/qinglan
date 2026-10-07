<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import FormField from '@/components/base/FormField.vue'
import { useAuth } from '@/composables/useAuth'
import { useCodeCooldown } from '@/composables/useCodeCooldown'

type Step = 'email' | 'code' | 'student'

/** 与后端 qinglan.auth.code-ttl 默认值保持一致，仅用于文案提示 */
const CODE_TTL_MINUTES = 5

/** 与后端学号规则一致：4-24 位字母或数字 */
const STUDENT_ID_PATTERN = /^[A-Za-z0-9]{4,24}$/

const STEP_LABELS = ['校园邮箱', '验证码', '绑定学号'] as const
const STEP_INDEX: Record<Step, number> = { email: 0, code: 1, student: 2 }

const router = useRouter()
const route = useRoute()
const { sending, verifying, binding, devCode, requestCode, verify, bind } = useAuth()
const { remaining: resendRemaining, start: startResendCooldown } = useCodeCooldown()

const step = ref<Step>('email')
const email = ref('')
const code = ref('')
const studentId = ref('')

const submitting = computed(() => sending.value || verifying.value || binding.value)
const stepIndex = computed(() => STEP_INDEX[step.value])
/** 学号只在客户端做与后端一致的格式预校验（真正拦截在服务端） */
const studentIdValid = computed(() => STUDENT_ID_PATTERN.test(studentId.value.trim()))

/**
 * 执行认证动作，失败返回 null。
 * 错误提示已由 http 拦截器统一弹窗（AGENTS.md 第七章），这里只关心是否成功以决定是否推进步骤。
 */
async function attempt<T>(action: () => Promise<T>): Promise<T | null> {
  try {
    return await action()
  } catch {
    return null
  }
}

function afterAuthenticated(): void {
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
</script>

<template>
  <BaseCard class="mx-auto mt-10 w-full max-w-[440px] max-sm:mt-4">
    <div class="flex items-center gap-3">
      <span class="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
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

    <ol class="mt-6 flex items-center gap-2">
      <li v-for="(label, index) in STEP_LABELS" :key="label" class="flex items-center gap-2">
        <!-- 圆圈是「已完成 / 当前」步骤的专属样式：未激活态不画圆，只留灰色数字。
             浅色圆对白底只有 1.08:1，低于非文本组件 3:1 的门槛，画了也等于没画（T-06） -->
        <span
          class="flex size-5 items-center justify-center rounded-full text-xs font-semibold transition"
          :class="index <= stepIndex ? 'bg-primary-deep text-white' : 'text-muted'"
        >
          {{ index + 1 }}
        </span>
        <span class="text-xs transition" :class="index <= stepIndex ? 'text-ink' : 'text-muted'">
          {{ label }}
        </span>
        <span v-if="index < STEP_LABELS.length - 1" class="h-px w-4 bg-line-strong" aria-hidden="true" />
      </li>
    </ol>

    <form v-if="step === 'email'" class="mt-6 flex flex-col gap-4" @submit.prevent="handleSendCode">
      <FormField label="校园邮箱" hint="我们只会向该邮箱发送一次登录验证码">
        <BaseInput
          v-model="email"
          type="email"
          autocomplete="email"
          placeholder="name@qinglan.edu.cn"
          required
        />
      </FormField>
      <BaseButton html-type="submit" block :loading="submitting" :disabled="resendRemaining > 0">
        {{ resendRemaining > 0 ? `请 ${resendRemaining}s 后再试` : '获取验证码' }}
      </BaseButton>
    </form>

    <form v-else-if="step === 'code'" class="mt-6 flex flex-col gap-4" @submit.prevent="handleVerify">
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
      <BaseButton html-type="submit" block :loading="submitting">验证并登录</BaseButton>
      <BaseButton variant="ghost" size="sm" block @click="step = 'email'">更换邮箱</BaseButton>
    </form>

    <form v-else class="mt-6 flex flex-col gap-4" @submit.prevent="handleBind">
      <FormField
        label="学号"
        :hint="studentId && !studentIdValid ? '' : '4-24 位字母或数字，绑定后不可更改'"
        :error="studentId && !studentIdValid ? '学号须为 4-24 位字母或数字' : ''"
      >
        <BaseInput v-model="studentId" placeholder="请输入学号" required />
      </FormField>
      <BaseButton html-type="submit" block :loading="submitting" :disabled="!studentIdValid">
        完成认证
      </BaseButton>
    </form>
  </BaseCard>
</template>
