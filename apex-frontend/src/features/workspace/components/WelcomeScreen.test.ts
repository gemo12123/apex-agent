import { mount } from '@vue/test-utils'
import WelcomeScreen from '@/features/workspace/components/WelcomeScreen.vue'

describe('WelcomeScreen', () => {
  it('shows welcome copy, fills suggestions, and emits submit', async () => {
    const wrapper = mount(WelcomeScreen)

    expect(wrapper.text()).toContain('今天想让 Apex 做什么？')

    const suggestion = wrapper.findAll('button').at(0)
    expect(suggestion).toBeDefined()
    await suggestion!.trigger('click')

    const textarea = wrapper.get('textarea')
    expect((textarea.element as HTMLTextAreaElement).value.length).toBeGreaterThan(0)

    await textarea.setValue('Inspect the SSE contract')
    await textarea.trigger('keydown.enter')

    expect(wrapper.emitted('submit')?.[0]).toEqual(['Inspect the SSE contract'])
  })
})
