import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TodoBell from '@/components/admin/TodoBell.vue'
import type { TodoGroup } from '@/composables/useTodoCounts'

/** el-popover 走 Teleport + 定位，单测换成把参照物与内容直接铺开的桩 */
const ElPopoverStub = {
  template: '<div><slot name="reference" /><slot /></div>',
}

function groups(): TodoGroup[] {
  return [
    { key: 'pendingPosts', label: '新待审', count: 3, routeName: 'admin-pending-posts' },
    { key: 'proposable', label: '待表决', count: 2, routeName: 'admin-proposals' },
  ]
}

function mountBell(value: TodoGroup[]) {
  return mount(TodoBell, {
    props: { groups: value },
    global: { stubs: { ElPopover: ElPopoverStub } },
  })
}

describe('TodoBell', () => {
  it('渲染各分组的文案与计数，角标为总数', () => {
    const wrapper = mountBell(groups())

    expect(wrapper.text()).toContain('新待审')
    expect(wrapper.text()).toContain('待表决')
    // 角标 5 = 3 + 2
    expect(wrapper.text()).toContain('5')
  })

  it('点击分组 emit select 携带整组信息（跳转由父级决定）', async () => {
    const wrapper = mountBell(groups())

    const target = wrapper.findAll('button').find((button) => button.text().includes('待表决'))
    await target?.trigger('click')

    expect(wrapper.emitted('select')).toEqual([[groups()[1]]])
  })

  it('无分组时给出「暂无待办」占位，不渲染角标', () => {
    const wrapper = mountBell([])

    expect(wrapper.text()).toContain('暂无待办')
    expect(wrapper.find('span.bg-danger').exists()).toBe(false)
  })
})
