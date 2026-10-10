import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import CreditAdjustDialog from '@/components/admin/CreditAdjustDialog.vue'

/**
 * el-dialog / el-input 在单测里换成「可输入的真 DOM + 转发 update:modelValue」的桩，
 * 聚焦表单逻辑本身（本 spec 要盯的就是「填完值按钮能不能点」）。
 *
 * 桩必须保留 `modelValue` / `update:modelValue`：丢掉这条唯一通道就会出现
 * 「单测全绿、浏览器里弹窗关不掉」的盲区。
 */
const ElDialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div><slot /><slot name="footer" /></div>',
}

const ElInputStub = {
  props: ['modelValue', 'type'],
  emits: ['update:modelValue'],
  template:
    '<div><textarea v-if="type === \'textarea\'" class="stub-textarea" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else class="stub-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /></div>',
}

function mountDialog() {
  return mount(CreditAdjustDialog, {
    props: { open: true },
    global: { stubs: { ElDialog: ElDialogStub, ElInput: ElInputStub } },
  })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

describe('CreditAdjustDialog', () => {
  it('填入范围内非 0 的整数与理由后可提交，emit submit(分值, 理由)', async () => {
    const wrapper = mountDialog()

    await wrapper.get('input.stub-input').setValue('10')
    await wrapper.get('textarea.stub-textarea').setValue('灌水扣分')
    await buttonByText(wrapper, '确认调整').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[10, '灌水扣分']])
  })

  it('分值为 0、超范围或非整数时按钮保持禁用', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea.stub-textarea').setValue('理由')

    for (const value of ['0', '101', '-101', '1.5']) {
      await wrapper.get('input.stub-input').setValue(value)
      expect(buttonByText(wrapper, '确认调整').attributes('disabled')).toBeDefined()
    }
  })

  it('分值与理由都为空时按钮禁用', () => {
    const wrapper = mountDialog()

    expect(buttonByText(wrapper, '确认调整').attributes('disabled')).toBeDefined()
  })

  it('把 open 透传给 el-dialog，并响应它抛出的关闭事件', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.findComponent(ElDialogStub)

    expect(dialog.props('modelValue')).toBe(true)

    dialog.vm.$emit('update:modelValue', false)
    await nextTick()

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
