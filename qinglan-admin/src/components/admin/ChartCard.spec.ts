import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ChartCard from './ChartCard.vue'

describe('ChartCard', () => {
  it('有数据时渲染标题与默认插槽（图表），不出现空态', () => {
    const wrapper = mount(ChartCard, {
      props: { title: '处置趋势', hasData: true },
      slots: { default: '<div class="chart-stub" />' },
    })

    expect(wrapper.text()).toContain('处置趋势')
    expect(wrapper.find('.chart-stub').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('暂无数据')
  })

  it('无数据时只渲染空态，默认插槽内容不渲染', () => {
    const wrapper = mount(ChartCard, {
      props: { title: '处置趋势', hasData: false },
      slots: { default: '<div class="chart-stub" />' },
    })

    expect(wrapper.find('.chart-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('action 插槽渲染在标题右侧（如范围切换 / 通过率）', () => {
    const wrapper = mount(ChartCard, {
      props: { title: '提案表决概况', hasData: true },
      slots: { action: '<span class="rate">75%</span>' },
    })

    expect(wrapper.find('.rate').text()).toBe('75%')
  })
})
