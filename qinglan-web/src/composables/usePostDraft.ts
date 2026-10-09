import { ref, watch, type Ref } from 'vue'

/**
 * 发帖草稿（阶段 2）：「提交前草稿自动保存到 localStorage，意外退出不丢内容」。
 *
 * 设计要点：
 * - **不静默恢复**：检出已有草稿时只置 `pending`，由页面给出「恢复 / 丢弃」两个显式动作；
 *   在用户决定之前**暂停自动保存**，避免把用户新输入覆盖到旧草稿上（旧草稿仍在，直到用户选择）。
 * - 只保存有实质内容的草稿（正文 / 联系方式 / 图片任一非空），仅剩一个话题 id 不算草稿。
 */
export interface PostDraft {
  topicId: number | null
  content: string
  contact: string
  images: string[]
}

/** 本机草稿键。草稿只在当前浏览器有效，多端不同步（方案第 11 章已记此限制） */
const STORAGE_KEY = 'qinglan:publish-draft'

export { STORAGE_KEY as POST_DRAFT_STORAGE_KEY }

function readDraft(): PostDraft | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Partial<PostDraft>
    return {
      topicId: typeof parsed.topicId === 'number' ? parsed.topicId : null,
      content: typeof parsed.content === 'string' ? parsed.content : '',
      contact: typeof parsed.contact === 'string' ? parsed.contact : '',
      images: Array.isArray(parsed.images)
        ? parsed.images.filter((item): item is string => typeof item === 'string')
        : [],
    }
  } catch {
    // 数据损坏（旧版本格式 / 手工改坏）时按「没有草稿」处理，不阻塞发布页
    return null
  }
}

/** 是否值得恢复：正文、联系方式、图片任一非空 */
function hasContent(draft: PostDraft): boolean {
  return draft.content.trim().length > 0 || draft.contact.trim().length > 0 || draft.images.length > 0
}

export function usePostDraft(fields: {
  topicId: Ref<number | null>
  content: Ref<string>
  contact: Ref<string>
  images: Ref<string[]>
}) {
  const stored = readDraft()
  const pending = ref<PostDraft | null>(stored && hasContent(stored) ? stored : null)
  if (stored && !hasContent(stored)) {
    localStorage.removeItem(STORAGE_KEY)
  }

  function save(): void {
    const next: PostDraft = {
      topicId: fields.topicId.value,
      content: fields.content.value,
      contact: fields.contact.value,
      images: [...fields.images.value],
    }
    if (hasContent(next)) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    } else {
      // 用户清空后不再留空草稿：下次进入不应看到「恢复」空内容
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  // 有未决草稿时暂停自动保存：用户须先「恢复」或「丢弃」，否则新输入会覆盖旧草稿
  watch(
    [fields.topicId, fields.content, fields.contact, fields.images],
    () => {
      if (pending.value) {
        return
      }
      save()
    },
    { deep: true },
  )

  function restore(): void {
    const draft = pending.value
    if (!draft) {
      return
    }
    fields.topicId.value = draft.topicId
    fields.content.value = draft.content
    fields.contact.value = draft.contact
    fields.images.value = [...draft.images]
    pending.value = null
  }

  function discard(): void {
    pending.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  /** 发布成功后清空草稿 */
  function clear(): void {
    pending.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return { pending, restore, discard, clear }
}
