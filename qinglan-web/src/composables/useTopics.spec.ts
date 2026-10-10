import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchTopics } from '@/api/topic'
import { useTopics } from './useTopics'
import type { Topic } from '@/types/post'

vi.mock('@/api/topic', () => ({ fetchTopics: vi.fn() }))

const mockedFetchTopics = vi.mocked(fetchTopics)

function topic(id: number): Topic {
  return { id, name: `话题${id}`, rules: `话题${id}的规则`, requiresContact: false }
}

describe('useTopics', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('拉取成功后写入列表，失败位为假', async () => {
    mockedFetchTopics.mockResolvedValue([topic(1)])

    const { topics, topicsFailed, loadTopics } = useTopics()
    await loadTopics()

    expect(topics.value).toEqual([topic(1)])
    expect(topicsFailed.value).toBe(false)
  })

  it('拉取失败时置失败位、列表保持为空（不抛错）', async () => {
    mockedFetchTopics.mockRejectedValue(new Error('网络异常'))

    const { topics, topicsFailed, loadTopics } = useTopics()
    await loadTopics()

    expect(topics.value).toEqual([])
    expect(topicsFailed.value).toBe(true)
  })

  it('重试成功会清掉上一次的失败位', async () => {
    mockedFetchTopics.mockRejectedValueOnce(new Error('网络异常'))

    const { topics, topicsFailed, loadTopics } = useTopics()
    await loadTopics()
    expect(topicsFailed.value).toBe(true)

    mockedFetchTopics.mockResolvedValue([topic(2)])
    await loadTopics()

    expect(topics.value).toEqual([topic(2)])
    expect(topicsFailed.value).toBe(false)
  })
})
