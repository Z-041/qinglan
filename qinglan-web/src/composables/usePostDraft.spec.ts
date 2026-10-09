import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'

import { POST_DRAFT_STORAGE_KEY, usePostDraft } from '@/composables/usePostDraft'

function setup() {
  const topicId = ref<number | null>(null)
  const content = ref('')
  const contact = ref('')
  const images = ref<string[]>([])
  const draft = usePostDraft({ topicId, content, contact, images })
  return { topicId, content, contact, images, draft }
}

function readStored(): { content?: string; images?: string[] } | null {
  const raw = localStorage.getItem(POST_DRAFT_STORAGE_KEY)
  return raw ? (JSON.parse(raw) as { content?: string; images?: string[] }) : null
}

describe('usePostDraft', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('字段变更后自动写入草稿，且保存图片列表', async () => {
    const { content, images } = setup()

    content.value = '写了一半'
    images.value = ['/uploads/a.jpg']
    await nextTick()

    const stored = readStored()
    expect(stored?.content).toBe('写了一半')
    expect(stored?.images).toEqual(['/uploads/a.jpg'])
  })

  it('清空所有内容后移除草稿，不留空草稿', async () => {
    const { content } = setup()

    content.value = '写了一半'
    await nextTick()
    expect(localStorage.getItem(POST_DRAFT_STORAGE_KEY)).not.toBeNull()

    content.value = ''
    await nextTick()
    expect(localStorage.getItem(POST_DRAFT_STORAGE_KEY)).toBeNull()
  })

  it('已有草稿时只置 pending，不自动回填字段（不静默覆盖用户新输入）', () => {
    localStorage.setItem(
      POST_DRAFT_STORAGE_KEY,
      JSON.stringify({ topicId: 1, content: '旧草稿', contact: '', images: [] }),
    )

    const { content, draft } = setup()

    expect(draft.pending.value?.content).toBe('旧草稿')
    expect(content.value).toBe('')
  })

  it('存在未决草稿时暂停自动保存，避免覆盖旧草稿', async () => {
    localStorage.setItem(
      POST_DRAFT_STORAGE_KEY,
      JSON.stringify({ topicId: 1, content: '旧草稿', contact: '', images: [] }),
    )

    const { content } = setup()
    content.value = '新输入'
    await nextTick()

    // 用户尚未选择恢复/丢弃，旧草稿不能被新输入覆盖
    expect(readStored()?.content).toBe('旧草稿')
  })

  it('恢复草稿把字段回填并结束 pending', () => {
    localStorage.setItem(
      POST_DRAFT_STORAGE_KEY,
      JSON.stringify({ topicId: 2, content: '旧草稿', contact: 'wx:1', images: ['/uploads/a.jpg'] }),
    )

    const { topicId, content, contact, images, draft } = setup()
    draft.restore()

    expect(topicId.value).toBe(2)
    expect(content.value).toBe('旧草稿')
    expect(contact.value).toBe('wx:1')
    expect(images.value).toEqual(['/uploads/a.jpg'])
    expect(draft.pending.value).toBeNull()
  })

  it('丢弃草稿清空本地存储并结束 pending', () => {
    localStorage.setItem(
      POST_DRAFT_STORAGE_KEY,
      JSON.stringify({ topicId: 1, content: '旧草稿', contact: '', images: [] }),
    )

    const { draft } = setup()
    draft.discard()

    expect(draft.pending.value).toBeNull()
    expect(localStorage.getItem(POST_DRAFT_STORAGE_KEY)).toBeNull()
  })

  it('发布成功后 clear 清空草稿', async () => {
    const { content, draft } = setup()
    content.value = '要发布的正文'
    await nextTick()
    expect(localStorage.getItem(POST_DRAFT_STORAGE_KEY)).not.toBeNull()

    draft.clear()
    expect(localStorage.getItem(POST_DRAFT_STORAGE_KEY)).toBeNull()
  })

  it('损坏的草稿数据按「没有草稿」处理，不抛错', () => {
    localStorage.setItem(POST_DRAFT_STORAGE_KEY, '{不是合法 JSON')

    const { draft } = setup()

    expect(draft.pending.value).toBeNull()
  })
})
