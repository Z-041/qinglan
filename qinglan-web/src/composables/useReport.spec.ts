import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createReport } from '@shared/api/report'
import { useReport } from '@/composables/useReport'

vi.mock('@shared/api/report', () => ({ createReport: vi.fn(), submitAppeal: vi.fn() }))

const mockedCreateReport = vi.mocked(createReport)

describe('useReport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('未指定目标时提交直接失败，不发请求', async () => {
    const report = useReport()

    await expect(report.submit('ABUSE', '')).resolves.toBe(false)

    expect(mockedCreateReport).not.toHaveBeenCalled()
  })

  it('提交时带上目标类型与 id，并把纯空白说明归一为 null', async () => {
    mockedCreateReport.mockResolvedValue(undefined)
    const report = useReport()
    report.open({ targetType: 'COMMENT', targetId: 55, label: '这条评论' })

    await expect(report.submit('PRIVACY', '   ')).resolves.toBe(true)

    expect(mockedCreateReport).toHaveBeenCalledWith({
      targetType: 'COMMENT',
      targetId: 55,
      reasonCode: 'PRIVACY',
      detail: null,
    })
    expect(report.target.value).toBeNull()
    expect(report.submitting.value).toBe(false)
  })

  it('提交失败时保留弹层，便于用户改理由重试', async () => {
    mockedCreateReport.mockRejectedValue(new Error('已经举报过了'))
    const report = useReport()
    report.open({ targetType: 'POST', targetId: 9, label: '这条帖子' })

    await expect(report.submit('ABUSE', '骂人')).resolves.toBe(false)

    expect(report.target.value?.targetId).toBe(9)
    expect(report.submitting.value).toBe(false)
  })

  it('close 清空目标', () => {
    const report = useReport()
    report.open({ targetType: 'POST', targetId: 9, label: '这条帖子' })

    report.close()

    expect(report.target.value).toBeNull()
  })
})
