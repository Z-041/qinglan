import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PostImageGrid from './PostImageGrid.vue'

describe('PostImageGrid', () => {
  it('无图片时不渲染', () => {
    expect(mount(PostImageGrid, { props: { urls: [] } }).find('div').exists()).toBe(false)
  })

  it('thumb：四列小方块，逐张懒加载', () => {
    const wrapper = mount(PostImageGrid, { props: { urls: ['a.jpg', 'b.jpg'], variant: 'thumb' } })
    expect(wrapper.get('div').classes()).toContain('grid-cols-4')
    expect(wrapper.findAll('img')).toHaveLength(2)
    expect(wrapper.findAll('img')[0].attributes('loading')).toBe('lazy')
    expect(wrapper.findAll('img')[0].classes()).toContain('aspect-square')
  })

  it('detail 单图：单列、限高原比例', () => {
    const wrapper = mount(PostImageGrid, { props: { urls: ['only.jpg'], variant: 'detail' } })
    expect(wrapper.get('div').classes()).toContain('grid-cols-1')
    expect(wrapper.get('img').classes()).toContain('max-h-96')
    expect(wrapper.get('img').classes()).not.toContain('aspect-square')
  })

  it('detail 多图：三列方块', () => {
    const wrapper = mount(PostImageGrid, { props: { urls: ['a.jpg', 'b.jpg'], variant: 'detail' } })
    expect(wrapper.get('div').classes()).toContain('grid-cols-3')
    expect(wrapper.findAll('img')[1].classes()).toContain('aspect-square')
  })

  it('class 透传到网格容器，供调用方控制外距', () => {
    const wrapper = mount(PostImageGrid, {
      props: { urls: ['a.jpg'] },
      attrs: { class: 'mt-2' },
    })
    expect(wrapper.get('div').classes()).toContain('mt-2')
  })
})
