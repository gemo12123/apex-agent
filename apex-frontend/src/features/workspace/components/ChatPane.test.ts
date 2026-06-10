import { mount } from '@vue/test-utils'
import ChatPane from '@/features/workspace/components/ChatPane.vue'

describe('ChatPane', () => {
  it('shows the welcome state before the first user message and the transcript afterwards', async () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: false,
        messages: [],
        pendingPrompts: [],
        pendingConfirmations: [],
        status: 'idle',
      },
    })

    expect(wrapper.text()).toContain('今天想让 Apex 做什么？')

    await wrapper.setProps({
      hasStarted: true,
      messages: [
        { id: 'user-1', role: 'user', content: '总结执行流程' },
        { id: 'assistant-1', role: 'assistant', content: '这里是结果', think: '', flows: [] },
      ],
    })

    expect(wrapper.text()).toContain('总结执行流程')
    expect(wrapper.text()).toContain('这里是结果')
  })

  it('disables the composer while the session waits for confirmation', () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: true,
        messages: [],
        pendingPrompts: [],
        pendingConfirmations: [],
        status: 'waiting-confirmation',
      },
    })

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
  })
})
