import { mount } from '@vue/test-utils'
import WelcomeScreen from '@/features/workspace/components/WelcomeScreen.vue'

describe('WelcomeScreen', () => {
  it('shows welcome copy and emits fill-draft when a suggestion is clicked', async () => {
    const wrapper = mount(WelcomeScreen)

    expect(wrapper.text()).toContain('今天想让 Apex 做什么？')
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.find('[data-testid="welcome-suggestion-0"]').exists()).toBe(true)

    await wrapper.get('[data-testid="welcome-suggestion-0"]').trigger('click')

    expect(wrapper.emitted('fill-draft')?.[0]?.[0]).toContain('前端')
  })

  it('keeps the empty state lightweight and centered', () => {
    const wrapper = mount(WelcomeScreen)

    expect(wrapper.find('.welcome-screen__copy').exists()).toBe(true)
    expect(wrapper.find('.welcome-screen__suggestions').exists()).toBe(true)
    expect(wrapper.find('[data-testid="welcome-suggestion-0"]').exists()).toBe(true)
    expect(wrapper.find('.welcome-screen__composer').exists()).toBe(false)
  })
})
