import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'

import MessageBoxHost from './MessageBoxHost.vue'
import { resolveMessageBox, useMessageBox } from '@/composables/useMessageBox'

afterEach(() => {
  resolveMessageBox(false)
})

describe('MessageBoxHost', () => {
  it('无请求时不渲染；confirm 打开后点确认解析 true 并关闭', async () => {
    const wrapper = mount(MessageBoxHost)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    const pending = useMessageBox().confirm('要发布吗？', '发布公告', '确认发布')
    await nextTick()
    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('发布公告')
    expect(wrapper.text()).toContain('要发布吗？')

    await wrapper.findAll('button').find((b) => b.text() === '确认发布')!.trigger('click')
    await expect(pending).resolves.toBe(true)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('prompt：空值提交显示校验红字且不关闭，填值后提交解析结果', async () => {
    const wrapper = mount(MessageBoxHost)
    const pending = useMessageBox().promptReason('驳回帖子', '理由会发给作者')
    await nextTick()

    await wrapper.findAll('button').find((b) => b.text() === '确认')!.trigger('click')
    expect(wrapper.text()).toContain('理由不能为空')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    await wrapper.get('input').setValue('  违规内容  ')
    await wrapper.findAll('button').find((b) => b.text() === '确认')!.trigger('click')
    await expect(pending).resolves.toBe('违规内容')
  })

  it('取消按钮按取消处理，解析 null', async () => {
    const wrapper = mount(MessageBoxHost)
    const pending = useMessageBox().promptReason('驳回帖子', '理由会发给作者')
    await nextTick()

    await wrapper.findAll('button').find((b) => b.text() === '取消')!.trigger('click')
    await expect(pending).resolves.toBeNull()
  })
})
