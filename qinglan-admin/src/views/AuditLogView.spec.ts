import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { exportAuditLogs, fetchAuditLogs } from '@/api/admin'
import AuditLogView from '@/views/AuditLogView.vue'
import type { AdminAuditLogItem } from '@/types/admin'

vi.mock('@/api/admin', () => ({ fetchAuditLogs: vi.fn(), exportAuditLogs: vi.fn() }))

const mockedFetchAuditLogs = vi.mocked(fetchAuditLogs)
const mockedExportAuditLogs = vi.mocked(exportAuditLogs)

function log(overrides: Partial<AdminAuditLogItem> = {}): AdminAuditLogItem {
  return {
    id: 1,
    targetType: 'POST',
    targetId: 5,
    action: 'POST_TAKEDOWN',
    operatorId: 7,
    reason: 'ABUSE',
    createdAt: '2026-10-06T01:02:03Z',
    ...overrides,
  }
}

describe('AuditLogView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('审计时间保留绝对时间但压成短格式，完整时刻落在 title 上', async () => {
    const createdAt = '2026-10-06T01:02:03Z'
    mockedFetchAuditLogs.mockResolvedValue({ items: [log({ createdAt })], nextCursor: null })

    const wrapper = mount(AuditLogView)
    await flushPromises()

    // absolute-short：正文为 `YYYY-MM-DD HH:mm`，完整时刻在 title，datetime 为原始 ISO
    const time = wrapper.find(`time[datetime="${createdAt}"]`)
    expect(time.exists()).toBe(true)
    expect(time.attributes('title')).toMatch(/2026/)
    expect(time.text()).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
  })

  it('表头固定：thead 带 sticky + 不透明底色', async () => {
    mockedFetchAuditLogs.mockResolvedValue({ items: [], nextCursor: null })

    const wrapper = mount(AuditLogView)
    await flushPromises()

    const thead = wrapper.find('thead')
    expect(thead.classes()).toContain('sticky')
    expect(thead.classes()).toContain('bg-surface')
  })

  it('导出 CSV：按当前筛选调用导出接口，并按后端文件名落盘', async () => {
    mockedFetchAuditLogs.mockResolvedValue({ items: [], nextCursor: null })
    mockedExportAuditLogs.mockResolvedValue({ blob: new Blob(['a,b']), fileName: 'audit-logs-20261009-120000.csv' })
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    let clickedName = ''
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clickedName = this.download
    })

    const wrapper = mount(AuditLogView)
    await flushPromises()

    const exportButton = wrapper.findAll('button').find((button) => button.text() === '导出 CSV')
    await exportButton?.trigger('click')
    await flushPromises()

    // 默认无筛选：全部为空
    expect(mockedExportAuditLogs).toHaveBeenCalledWith({ action: null, from: null, to: null })
    expect(clickedName).toBe('audit-logs-20261009-120000.csv')

    vi.restoreAllMocks()
  })
})
