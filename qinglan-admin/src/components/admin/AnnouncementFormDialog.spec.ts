import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import AnnouncementFormDialog from '@/components/admin/AnnouncementFormDialog.vue'

/**
 * Element Plus 的弹窗/表单控件在单测里统一换成「可输入的真 DOM + 转发 update:modelValue」的桩，
 * 聚焦表单逻辑本身（真实 EP 组件的交互另有浏览器实测覆盖）。
 *
 * 弹窗桩**必须保留 `modelValue` / `update:modelValue` 这条唯一通道**：整体 stub 掉却丢掉它，
 * 会出现「单测全绿、浏览器里弹窗关不掉」的盲区——组件的开合契约从没被验证过。
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

const ElDatePickerStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input class="stub-date" :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', $event.target.value)" />',
}

/** 不渲染默认插槽：真实的 el-option 必须有 el-select 上下文，桩里渲染会报 usage 错 */
const ElSelectStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<select class="stub-select" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"></select>',
}

const ElCheckboxStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<label><input class="stub-checkbox" type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', !modelValue)" /><slot /></label>',
}

function mountDialog(): VueWrapper {
  return mount(AnnouncementFormDialog, {
    props: { open: true },
    global: {
      stubs: {
        ElDialog: ElDialogStub,
        ElInput: ElInputStub,
        ElDatePicker: ElDatePickerStub,
        ElSelect: ElSelectStub,
        ElCheckbox: ElCheckboxStub,
      },
    },
  })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

/** 标题与最短阅读秒数是两个 el-input，按出现顺序取 */
function textInput(wrapper: VueWrapper, index: number): DOMWrapper<HTMLInputElement> {
  return wrapper.findAll('input.stub-input')[index] as DOMWrapper<HTMLInputElement>
}

function datePickers(wrapper: VueWrapper): DOMWrapper<HTMLInputElement>[] {
  return wrapper.findAll('input.stub-date') as unknown as DOMWrapper<HTMLInputElement>[]
}

/** 填齐「标题 + 正文 + 生效开始」这三项必填 */
async function fillRequired(wrapper: VueWrapper): Promise<void> {
  await textInput(wrapper, 0).setValue('停水通知')
  await wrapper.get('textarea.stub-textarea').setValue('今晚 22:00 起停水')
  await datePickers(wrapper)[0].setValue('2026-10-08T10:00:00')
}

describe('AnnouncementFormDialog', () => {
  it('标题、正文、生效开始任一为空时提交按钮保持禁用', async () => {
    const wrapper = mountDialog()
    const submit = buttonByText(wrapper, '提交草稿')

    expect(submit.attributes('disabled')).toBeDefined()

    await textInput(wrapper, 0).setValue('停水通知')
    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.get('textarea.stub-textarea').setValue('今晚 22:00 起停水')
    expect(submit.attributes('disabled')).toBeDefined()

    await datePickers(wrapper)[0].setValue('2026-10-08T10:00:00')
    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('提交时生效时间转 ISO-8601，最短阅读秒数留空传 null', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)

    await buttonByText(wrapper, '提交草稿').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      title: '停水通知',
      content: '今晚 22:00 起停水',
      level: 'NORMAL',
      // el-date-picker 给的是本地时间且无时区，后端要 Instant
      startAt: new Date('2026-10-08T10:00:00').toISOString(),
      endAt: null,
      minReadSeconds: null,
      requireScrollToEnd: false,
      requireConfirm: false,
    })
  })

  it('最短阅读秒数填写范围内整数时提交为数字', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)
    await textInput(wrapper, 1).setValue('10')

    await buttonByText(wrapper, '提交草稿').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ minReadSeconds: 10 })
  })

  it('最短阅读秒数超出 0–60 时禁用提交并给出错误文案', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)
    await textInput(wrapper, 1).setValue('61')

    expect(buttonByText(wrapper, '提交草稿').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请输入 0–60 的整数')
  })

  it('生效结束早于或等于开始时间时禁用提交并给出错误文案', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)
    await datePickers(wrapper)[1].setValue('2026-10-08T09:00:00')

    expect(buttonByText(wrapper, '提交草稿').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('结束时间必须晚于开始时间')
  })

  it('把 open 透传给 el-dialog，并响应它抛出的关闭事件', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.findComponent(ElDialogStub)

    expect(dialog.props('modelValue')).toBe(true)

    dialog.vm.$emit('update:modelValue', false)
    await nextTick()

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('标题或正文未填时预览按钮禁用，都填好后可用（阶段 D 前置预览）', async () => {
    const wrapper = mountDialog()
    const preview = buttonByText(wrapper, '预览学生视角')

    expect(preview.attributes('disabled')).toBeDefined()

    await textInput(wrapper, 0).setValue('停水通知')
    await wrapper.get('textarea.stub-textarea').setValue('今晚 22:00 起停水')

    expect(preview.attributes('disabled')).toBeUndefined()
  })
})
