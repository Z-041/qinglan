import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseDrawer from './BaseDrawer.vue'

function mountDrawer(props: Record<string, unknown> = {}) {
  return mount(BaseDrawer, {
    props: { open: true, title: '审核上下文', ...props },
    slots: { default: '<p>面板内容</p>' },
  })
}

describe('BaseDrawer', () => {
  it('未打开时不渲染，打开时 role/aria-label 正确', () => {
    expect(mountDrawer({ open: false }).find('[role="dialog"]').exists()).toBe(false)

    const dialog = mountDrawer().get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-label')).toBe('审核上下文')
    expect(dialog.text()).toContain('面板内容')
  })

  it('面板宽度由 size 决定', () => {
    const wrapper = mountDrawer({ size: '640px' })
    expect(wrapper.get('aside').attributes('style')).toContain('max-width: 640px')
  })

  it('点遮罩 / ESC / 关闭按钮都抛 close', async () => {
    const wrapper = mountDrawer()
    await wrapper.get('[role="dialog"] > div').trigger('click')
    await wrapper.get('aside').trigger('keydown.escape')
    await wrapper.get('button[aria-label="关闭"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(3)
  })

  it('dismissible=false 时点遮罩与 ESC 不关闭（关闭按钮仍可用）', async () => {
    const wrapper = mountDrawer({ dismissible: false })
    await wrapper.get('[role="dialog"] > div').trigger('click')
    await wrapper.get('aside').trigger('keydown.escape')
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.get('button[aria-label="关闭"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
