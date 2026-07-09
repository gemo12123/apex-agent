import { mount } from '@vue/test-utils'
import WelcomeScreen from '@/features/workspace/components/WelcomeScreen.vue'

describe('WelcomeScreen', () => {
  it('shows welcome copy and emits fill-draft when a suggestion is clicked', async () => {
    const wrapper = mount(WelcomeScreen)

    expect(wrapper.text()).toContain('我们先从哪里开始呢？')
    expect(wrapper.text()).not.toContain('在同一条工作主列里发起任务、补充上下文，或继续推进上一轮执行结果。')
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.find('[data-testid="welcome-suggestion-0"]').exists()).toBe(true)

    await wrapper.get('[data-testid="welcome-suggestion-0"]').trigger('click')

    expect(wrapper.emitted('fill-draft')?.[0]?.[0]).toBe('生成图片')
  })

  it('keeps three icon suggestions in one compact row', () => {
    const wrapper = mount(WelcomeScreen)
    const suggestions = wrapper.findAll('.welcome-screen__suggestion')

    expect(wrapper.find('.welcome-screen__copy').exists()).toBe(true)
    expect(wrapper.find('.welcome-screen__suggestions--single-row').exists()).toBe(true)
    expect(suggestions).toHaveLength(3)
    expect(suggestions.map((suggestion) => suggestion.text())).toEqual([
      '生成图片',
      '撰写或编辑',
      '查找资料',
    ])
    expect(wrapper.findAll('.welcome-screen__suggestion-icon')).toHaveLength(3)
    expect(wrapper.find('.welcome-screen__composer').exists()).toBe(false)
  })
})
