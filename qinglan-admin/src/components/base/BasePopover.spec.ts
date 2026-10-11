import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'

import BasePopover from './BasePopover.vue'

const Host = defineComponent({
  components: { BasePopover },
  template: `
    <BasePopover :width="210">
      <template #trigger="{ toggle }">
        <button class="trigger" type="button" @click="toggle">铃铛</button>
      </template>
      <button class="item" type="button">待办项</button>
    </BasePopover>
  `,
})

function mountPopover() {
  return mount(Host, { attachTo: document.body })
}

describe('BasePopover', () => {
  it('初始不渲染内容，点 trigger 展开', async () => {
    const wrapper = mountPopover()
    expect(wrapper.find('.item').exists()).toBe(false)

    await wrapper.get('.trigger').trigger('click')
    expect(wrapper.get('.item').text()).toBe('待办项')

    await wrapper.get('.trigger').trigger('click')
    expect(wrapper.find('.item').exists()).toBe(false)
  })

  it('点外部关闭，点内容内部不关闭', async () => {
    const wrapper = mountPopover()
    await wrapper.get('.trigger').trigger('click')

    // 内容内部：不应关闭
    wrapper.get('.item').element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()
    expect(wrapper.find('.item').exists()).toBe(true)

    // 文档其它位置：关闭
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()
    expect(wrapper.find('.item').exists()).toBe(false)
  })

  it('ESC 关闭', async () => {
    const wrapper = mountPopover()
    await wrapper.get('.trigger').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.find('.item').exists()).toBe(false)
  })
})
