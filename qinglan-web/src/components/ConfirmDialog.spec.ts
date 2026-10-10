import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ConfirmDialog from './ConfirmDialog.vue'

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(ConfirmDialog, {
    props: { open: true, title: '确定删除这条评论吗？', message: '删除后不可恢复', ...props },
  })
}

describe('ConfirmDialog', () => {
  it('渲染标题、说明与默认按钮，aria-label 用标题', () => {
    const wrapper = mountDialog()
    expect(wrapper.text()).toContain('确定删除这条评论吗？')
    expect(wrapper.text()).toContain('删除后不可恢复')
    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('确定删除这条评论吗？')
    expect(wrapper.findAll('button').map((b) => b.text())).toEqual(['取消', '确认'])
  })

  it('确认 / 取消分别抛 confirm 与 close', async () => {
    const wrapper = mountDialog({ confirmText: '删除', danger: true })
    const buttons = wrapper.findAll('button')
    await buttons.find((b) => b.text() === '删除')!.trigger('click')
    await buttons.find((b) => b.text() === '取消')!.trigger('click')

    expect(wrapper.emitted('confirm')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('danger 时确认按钮为实心危险色', () => {
    const wrapper = mountDialog({ danger: true, confirmText: '删除' })
    const confirm = wrapper.findAll('button').find((b) => b.text() === '删除')
    expect(confirm?.classes()).toContain('bg-danger')
  })

  it('submitting 时遮罩与 ESC 都不关闭，按钮进入在途态', async () => {
    const wrapper = mountDialog({ submitting: true })
    await wrapper.get('[role="dialog"]').trigger('click')
    await wrapper.get('[role="dialog"]').trigger('keydown.escape')
    expect(wrapper.emitted('close')).toBeUndefined()
  })
})
