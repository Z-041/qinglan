import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import TakeDownDialog from '@/components/admin/TakeDownDialog.vue'

/**
 * el-dialog 走 Teleport 且懒渲染，单测里换成桩；桩要保留 `modelValue` / `update:modelValue`
 * 这条唯一通道，否则会出现「单测全绿、浏览器里弹窗关不掉」的盲区。
 */
const ElDialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div><slot /><slot name="footer" /></div>',
}

function mountDialog() {
  return mount(TakeDownDialog, {
    props: { open: true },
    global: { stubs: { ElDialog: ElDialogStub } },
  })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

describe('TakeDownDialog', () => {
  it('默认按「辱骂」下架且不封禁：banDays 传 null、说明留空也传 null', async () => {
    const wrapper = mountDialog()

    await buttonByText(wrapper, '确认下架').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([
      [{ reasonCode: 'ABUSE', detail: null, banDays: null }],
    ])
  })

  it('合法天数按数字上报，说明去空白', async () => {
    const wrapper = mountDialog()
    await wrapper.get('select').setValue('FRAUD')
    await wrapper.get('textarea').setValue('  诈骗  ')
    await wrapper.get('input[type="text"]').setValue('7')

    await buttonByText(wrapper, '确认下架').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([
      [{ reasonCode: 'FRAUD', detail: '诈骗', banDays: 7 }],
    ])
  })

  it.each(['0', '3660', '1.5', 'abc', '-3'])(
    '填了非法天数 %s 时禁用提交并给出提示，避免把非法值当「不封禁」静默丢掉',
    async (value) => {
      const wrapper = mountDialog()
      await wrapper.get('input[type="text"]').setValue(value)

      expect(buttonByText(wrapper, '确认下架').attributes('disabled')).toBeDefined()
      expect(wrapper.text()).toContain('请填 1-3650 的整数')
    },
  )

  it('清空天数后重新可提交，且 banDays 回到 null', async () => {
    const wrapper = mountDialog()
    const input = wrapper.get('input[type="text"]')

    await input.setValue('1.5')
    expect(buttonByText(wrapper, '确认下架').attributes('disabled')).toBeDefined()

    await input.setValue('')
    expect(buttonByText(wrapper, '确认下架').attributes('disabled')).toBeUndefined()

    await buttonByText(wrapper, '确认下架').trigger('click')
    expect(wrapper.emitted('submit')?.[0]).toEqual([
      { reasonCode: 'ABUSE', detail: null, banDays: null },
    ])
  })

  it('提交中时取消按钮禁用，避免处置请求在途时关掉弹窗', () => {
    const wrapper = mount(TakeDownDialog, {
      props: { open: true, submitting: true },
      global: { stubs: { ElDialog: ElDialogStub } },
    })

    expect(buttonByText(wrapper, '取消').element.disabled).toBe(true)
    expect(buttonByText(wrapper, '确认下架').element.disabled).toBe(true)
  })

  it('把 open 透传给 el-dialog，并响应它抛出的关闭事件', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.findComponent(ElDialogStub)

    expect(dialog.props('modelValue')).toBe(true)

    dialog.vm.$emit('update:modelValue', false)
    await nextTick()

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('重新打开时回到初始态，不残留上一轮的理由与天数', async () => {
    const wrapper = mountDialog()
    await wrapper.get('input[type="text"]').setValue('30')
    await wrapper.get('textarea').setValue('上一轮的说明')

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect((wrapper.get('input[type="text"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  })
})
