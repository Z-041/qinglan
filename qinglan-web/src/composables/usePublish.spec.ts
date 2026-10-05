import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createPost } from '@/api/post'
import { fetchTopics } from '@/api/topic'
import { usePublish } from '@/composables/usePublish'
import type { PostDetail, Topic } from '@/types/post'

vi.mock('@/api/post', () => ({
  createPost: vi.fn(),
}))

vi.mock('@/api/topic', () => ({
  fetchTopics: vi.fn(),
}))

const mockedCreatePost = vi.mocked(createPost)
const mockedFetchTopics = vi.mocked(fetchTopics)

function topic(id: number, requiresContact = false): Topic {
  return { id, name: `话题${id}`, rules: `话题${id}的规则`, requiresContact }
}

const created: PostDetail = {
  id: 12,
  topicId: 1,
  topicName: '话题1',
  topicRules: '话题1的规则',
  content: '正文',
  contact: null,
  contactVisible: false,
  images: [],
  anonymousCode: '银杏#12',
  likeCount: 0,
  liked: false,
  commentCount: 0,
  mine: true,
  reviewReason: null,
  createdAt: '2026-10-06T00:00:00Z',
}

describe('usePublish', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载话题字典，并把选中话题解析为完整对象', async () => {
    mockedFetchTopics.mockResolvedValue([topic(1), topic(2, true)])

    const publish = usePublish()
    await publish.loadTopics()
    publish.topicId.value = 2

    expect(publish.topics.value).toHaveLength(2)
    expect(publish.selectedTopic.value?.requiresContact).toBe(true)
    expect(publish.loadingTopics.value).toBe(false)
  })

  it('未选话题或正文为空时不允许提交', async () => {
    const publish = usePublish()

    expect(publish.canSubmit.value).toBe(false)

    publish.topicId.value = 1
    expect(publish.canSubmit.value).toBe(false)

    publish.content.value = '正文'
    expect(publish.canSubmit.value).toBe(true)
  })

  it('正文里出现手机号时禁止提交并给出提示', () => {
    const publish = usePublish()
    publish.topicId.value = 1
    publish.content.value = '有意者联系 13800138000'

    expect(publish.blockedReason.value).toBe('正文里出现了手机号，请填写到下方「联系方式」栏')
    expect(publish.canSubmit.value).toBe(false)

    publish.content.value = '有意者看联系方式栏'
    expect(publish.blockedReason.value).toBeNull()
    expect(publish.canSubmit.value).toBe(true)
  })

  it('要求联系方式的话题在留空时不允许提交', async () => {
    mockedFetchTopics.mockResolvedValue([topic(2, true)])

    const publish = usePublish()
    await publish.loadTopics()
    publish.topicId.value = 2
    publish.content.value = '失物招领：捡到一把钥匙'

    expect(publish.contactRequired.value).toBe(true)
    expect(publish.canSubmit.value).toBe(false)

    publish.contact.value = '微信 qinglan'
    expect(publish.canSubmit.value).toBe(true)
  })

  it('提交时裁剪空白并把图片地址一并发出，成功返回详情', async () => {
    mockedCreatePost.mockResolvedValue(created)

    const publish = usePublish()
    publish.topicId.value = 1
    publish.content.value = '  正文内容  '
    publish.contact.value = '  wx:qinglan  '
    publish.images.value = ['/uploads/a.jpg']

    const result = await publish.submit()

    expect(mockedCreatePost).toHaveBeenCalledWith({
      topicId: 1,
      content: '正文内容',
      contact: 'wx:qinglan',
      images: ['/uploads/a.jpg'],
    })
    expect(result).toEqual(created)
    expect(publish.submitting.value).toBe(false)
  })

  it('联系方式为空时提交 null，不发送空字符串', async () => {
    mockedCreatePost.mockResolvedValue(created)

    const publish = usePublish()
    publish.topicId.value = 1
    publish.content.value = '正文'
    publish.contact.value = '   '

    await publish.submit()

    expect(mockedCreatePost).toHaveBeenCalledWith({
      topicId: 1,
      content: '正文',
      contact: null,
      images: [],
    })
  })

  it('提交失败返回 null 且复位 submitting', async () => {
    mockedCreatePost.mockRejectedValue(new Error('网络异常'))

    const publish = usePublish()
    publish.topicId.value = 1
    publish.content.value = '正文'

    await expect(publish.submit()).resolves.toBeNull()
    expect(publish.submitting.value).toBe(false)
  })

  it('不满足提交条件时不发请求', async () => {
    const publish = usePublish()
    publish.topicId.value = 1

    await expect(publish.submit()).resolves.toBeNull()
    expect(mockedCreatePost).not.toHaveBeenCalled()
  })
})
