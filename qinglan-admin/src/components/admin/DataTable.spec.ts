import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import DataTable from './DataTable.vue'

const HEAD = '<th class="px-3 py-2.5 font-medium">列一</th><th>列二</th><th>列三</th>'

/** 默认插槽留空——真实调用方用 v-for 渲染行，无数据时插槽本身不产出任何行 */
function mountTable(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(DataTable, {
    props: { items: [] as unknown[], colspan: 3, ...props },
    slots: { head: HEAD, ...slots },
  })
}

describe('DataTable', () => {
  it('表头带 sticky + 不透明底色，列由 head 插槽给出', () => {
    const thead = mountTable().get('thead')
    expect(thead.classes()).toEqual(expect.arrayContaining(['sticky', 'top-0', 'bg-surface']))
    expect(mountTable().findAll('th')).toHaveLength(3)
  })

  it('无数据时渲染占位行，文案随状态切换（加载中 / 加载失败 / 空态）', () => {
    expect(mountTable().get('tbody td').text()).toBe('暂无数据')
    expect(mountTable({ emptyText: '暂无待审帖子' }).get('tbody td').text()).toBe('暂无待审帖子')
    expect(mountTable({ loading: true }).get('tbody td').text()).toBe('加载中…')
    expect(mountTable({ failed: true }).get('tbody td').text()).toContain('加载失败')
  })

  it('占位列跨列数取 colspan，避免表头与占位错位', () => {
    expect(mountTable({ colspan: 6 }).get('tbody td').attributes('colspan')).toBe('6')
  })

  it('有数据时不渲染占位行，默认插槽的行照常渲染', () => {
    const wrapper = mountTable({ items: [{ id: 1 }] }, { default: '<tr class="row"><td>一行</td></tr>' })
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.text()).toContain('一行')
    expect(wrapper.text()).not.toContain('暂无数据')
  })

  it('加载中即便已有数据也不遮挡已有内容（占位行只在空列表出现）', () => {
    const wrapper = mountTable({ items: [{ id: 1 }], loading: true }, { default: '<tr><td>一行</td></tr>' })
    expect(wrapper.text()).toContain('一行')
    expect(wrapper.text()).not.toContain('加载中…')
  })
})
