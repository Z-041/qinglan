import type { ReportReason, ReviewTargetType } from '@shared/types/review-common'
import type { UserRole, UserStatus } from '@shared/types/user'

/** 后端以英文枚举下发角色，界面不得直接展示，统一在此转成中文 */
const ROLE_LABELS: Record<UserRole, string> = {
  STUDENT: '学生',
  REVIEWER: '审核员',
  ADMIN: '管理员',
  SUPER_ADMIN: '超级管理员',
}

/** 用户状态同上 */
const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: '正常',
  BANNED: '已封禁',
  BLACKLISTED: '已拉黑',
}

/** 举报理由与管理员处置的违规类型共用一套枚举，界面只此一处映射 */
const REPORT_REASON_LABELS: Record<ReportReason, string> = {
  ADVERTISEMENT: '广告与推广',
  PORNOGRAPHY: '色情低俗',
  ABUSE: '辱骂、人身攻击',
  PRIVACY: '泄露他人隐私',
  FRAUD: '诈骗与欺诈',
  ILLEGAL: '违法违规',
  IRRELEVANT: '与话题无关',
  OTHER: '其他',
}

/**
 * 后端写入 `post.reviewReason` 的**下架**原因码。
 *
 * 下架存「违规类型码[: 说明]」，故全部取值就是违规类型枚举 + 自动下架码。
 * 驳回复用同一列但存审核员填的自由文本，不走这里，所以不登记 `MANUAL_REJECT`。
 * 原因码不回收（R-20 I），前端仍要能容忍未知码——版本不同步时宁可退化也不能渲染出裸码。
 */
const REVIEW_CODE_LABELS: Record<string, string> = {
  AUTO_REPORT_THRESHOLD: '被多次举报，待复核',
  ...REPORT_REASON_LABELS,
}

/** 举报/申诉目标类型 */
const TARGET_TYPE_LABELS: Record<ReviewTargetType, string> = {
  POST: '帖子',
  COMMENT: '评论',
}

/**
 * 目标状态码 → 文案。帖子（PostStatus）与评论（CommentStatus）两套枚举取值不冲突，
 * 合在一张表里渲染；未知码同样退化为「未知」，与其它枚举一致。
 */
const TARGET_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  REVIEWING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  TAKEN_DOWN: '已下架',
  PUBLISHED: '已发布',
  REMOVED: '已移除',
}

/** 枚举由后端下发，前端可能先于版本拿到未知值，必须有兜底，不能渲染出 undefined */
const UNKNOWN_LABEL = '未知'

/** 用户相关枚举的中文标签，供各页面共用 */
export function useUserLabels() {
  function roleLabel(role: UserRole): string {
    return ROLE_LABELS[role] ?? UNKNOWN_LABEL
  }

  function statusLabel(status: UserStatus): string {
    return STATUS_LABELS[status] ?? UNKNOWN_LABEL
  }

  function reportReasonLabel(reason: ReportReason): string {
    return REPORT_REASON_LABELS[reason] ?? UNKNOWN_LABEL
  }

  /**
   * 把**下架原因**转成可读文案。
   *
   * 下架时 `post.reviewReason` 存的是 `违规类型码[: 说明]`；驳回复用的是同一个列，
   * 但存的是审核员填的自由文本，因此那条路径直接原样展示，不要经由此函数。
   * 未知码退化为通用文案，宁可少说也不把裸码渲染给用户。
   */
  function reviewReasonText(reason: string | null): string | null {
    if (!reason) {
      return null
    }
    const [code, ...rest] = reason.split(':')
    const detail = rest.join(':').trim()
    const label = REVIEW_CODE_LABELS[code.trim()]
    if (label && detail) {
      return `${label}：${detail}`
    }
    if (label) {
      return label
    }
    return detail || '因违规被处理'
  }

  function targetTypeLabel(targetType: ReviewTargetType): string {
    return TARGET_TYPE_LABELS[targetType] ?? UNKNOWN_LABEL
  }

  function targetStatusLabel(status: string): string {
    return TARGET_STATUS_LABELS[status] ?? UNKNOWN_LABEL
  }

  return {
    roleLabel,
    statusLabel,
    reportReasonLabel,
    reviewReasonText,
    targetTypeLabel,
    targetStatusLabel,
  }
}
