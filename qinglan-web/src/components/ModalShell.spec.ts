import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ModalShell from './ModalShell.vue'

function mountShell(props: Record<string, unknown> = {}) {
  return mount(ModalShell, {
    props: { open: true, label: '测试弹层', ...props },
    slots: { default: '<p>面板内容</p>' },
  })
}

describe('ModalShell', () => {
  it('未打开时不渲染，role/aria-label 正确', () => {
    expect(mountShell({ open: false }).find('[role="dialog"]').exists()).toBe(false)

    const dialog = mountShell().get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-label')).toBe('测试弹层')
    expect(dialog.classes()).toEqual(expect.arrayContaining(['overscroll-contain']))
  })

  it('点遮罩（自身）抛 close，点面板内容不抛', async () => {
    const wrapper = mountShell()
    await wrapper.get('p').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.get('[role="dialog"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('close-on-backdrop=false 时点遮罩不关闭；ESC 同理受 close-on-esc 控制', async () => {
    const wrapper = mountShell({ closeOnBackdrop: false })
    await wrapper.get('[role="dialog"]').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.get('[role="dialog"]').trigger('keydown.escape')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('ESC 受控：close-on-esc=false 时不关闭', async () => {
    const wrapper = mountShell({ closeOnEsc: false })
    await wrapper.get('[role="dialog"]').trigger('keydown.escape')
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('panelClass 覆盖面板宽度', () => {
    const wrapper = mountShell({ panelClass: 'max-w-sm' })
    expect(wrapper.get('[role="dialog"] > div').classes()).toContain('max-w-sm')
  })
})
