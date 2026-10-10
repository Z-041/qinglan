import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReasonDialogShell from './ReasonDialogShell.vue'

/** el-dialog 走 Teleport 且懒渲染，单测桩成仅按 modelValue 渲染插槽与 footer */
const ElDialogStub = {
  props: ['modelValue', 'title', 'width'],
  emits: ['update:modelValue'],
  template:
    '<div v-if="modelValue" :data-title="title"><slot /><slot name="footer" /></div>',
}

function mountShell(props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) {
  return mount(ReasonDialogShell, {
    props: { open: true, title: '测试弹窗', ...props },
    slots: { default: '<p>字段区</p>', ...slots },
    global: { stubs: { ElDialog: ElDialogStub } },
  })
}

describe('ReasonDialogShell', () => {
  it('渲染标题与默认插槽，footer 默认为取消 + 确认双按钮', () => {
    const wrapper = mountShell()
    expect(wrapper.text()).toContain('字段区')
    expect(wrapper.find('div[data-title="测试弹窗"]').exists()).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === '取消')).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === '确认')).toBe(true)
  })

  it('按钮文案与变体可覆盖，确认禁用 / 在途态透传', () => {
    const wrapper = mountShell({
      confirmText: '确认封禁',
      confirmVariant: 'danger',
      confirmDisabled: true,
    })
    const confirm = wrapper.findAll('button').find((b) => b.text() === '确认封禁')
    expect(confirm).toBeTruthy()
    expect(confirm?.attributes('disabled')).toBeDefined()
    expect(confirm?.classes()).toContain('bg-danger')
  })

  it('点确认抛 confirm、点取消抛 close', async () => {
    const wrapper = mountShell()
    const buttons = wrapper.findAll('button')
    await buttons.find((b) => b.text() === '确认')!.trigger('click')
    await buttons.find((b) => b.text() === '取消')!.trigger('click')
    expect(wrapper.emitted('confirm')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('非文本域按 Enter 触发确认；TEXTAREA 内 Enter 放行换行', async () => {
    const wrapper = mountShell({}, { default: '<textarea></textarea><input />' })

    await wrapper.get('input').trigger('keydown.enter')
    expect(wrapper.emitted('confirm')).toHaveLength(1)

    await wrapper.get('textarea').trigger('keydown.enter')
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('confirmDisabled 时 Enter 不触发确认', async () => {
    const wrapper = mountShell({ confirmDisabled: true }, { default: '<input />' })
    await wrapper.get('input').trigger('keydown.enter')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('enter-to-confirm=false 时任何 Enter 都不触发（双提交目标弹窗）', async () => {
    const wrapper = mountShell({ enterToConfirm: false }, { default: '<input />' })
    await wrapper.get('input').trigger('keydown.enter')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('自定义 #footer 完全替换默认按钮', () => {
    const wrapper = mountShell(
      {},
      { footer: '<button type="button">同意</button><button type="button">驳回</button>' },
    )
    expect(wrapper.text()).not.toContain('取消')
    expect(wrapper.text()).toContain('同意')
    expect(wrapper.text()).toContain('驳回')
  })
})
