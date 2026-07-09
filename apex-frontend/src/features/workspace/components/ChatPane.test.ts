import { mount } from '@vue/test-utils'
import ChatPane from '@/features/workspace/components/ChatPane.vue'

describe('ChatPane', () => {
  it('renders the empty state above the shared composer and fills the draft from suggestions', async () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: false,
        messages: [],
        pendingPrompts: [],
        pendingConfirmations: [],
        status: 'idle',
      },
    })

    expect(wrapper.text()).toContain('我们先从哪里开始呢？')
    expect(wrapper.find('.chat-pane__composer-shell').exists()).toBe(true)
    expect(wrapper.find('.chat-pane__composer-shell--prompt-bar').exists()).toBe(true)
    expect(wrapper.get('textarea').attributes('placeholder')).toBe('有问题，尽管问')
    expect(wrapper.find('[data-testid="add-context-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="voice-button"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="send-button"]').text()).toBe('开始')
    expect(wrapper.find('.chat-pane__hint').exists()).toBe(false)

    await wrapper.get('[data-testid="welcome-suggestion-0"]').trigger('click')

    const textarea = wrapper.get('textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('生成图片')
  })

  it('switches into transcript mode without moving the shared composer', () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: true,
        messages: [
          { id: 'user-1', role: 'user', content: '总结执行流程' },
          { id: 'assistant-1', role: 'assistant', content: '这里是结果', think: '', flows: [] },
        ],
        pendingPrompts: [],
        pendingConfirmations: [],
        status: 'completed',
      },
    })

    expect(wrapper.find('.chat-pane__transcript').exists()).toBe(true)
    expect(wrapper.find('.chat-message--assistant .chat-message__card').exists()).toBe(true)
    expect(wrapper.find('.chat-pane__composer-shell').exists()).toBe(true)
    expect(wrapper.find('.chat-pane__composer-shell--prompt-bar').exists()).toBe(true)
    expect(wrapper.get('textarea').attributes('rows')).toBe('1')
    expect(wrapper.get('[data-testid="send-button"]').text()).toBe('发送')
    expect(wrapper.find('.chat-pane__hint').exists()).toBe(false)
  })

  it('disables the shared composer while waiting for confirmation', () => {
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
    expect(wrapper.get('[data-testid="send-button"]').attributes('disabled')).toBeDefined()
  })

  it('keeps pending confirmations and prompts inside the transcript column', () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: true,
        messages: [],
        pendingPrompts: [
          {
            id: 'prompt-1',
            index: 0,
            toolCallId: 'tool-1',
            inputType: 'CONFIRM',
            question: '是否继续？',
            description: '需要人工确认',
            options: [],
            answered: false,
          },
        ],
        pendingConfirmations: [
          {
            id: 'confirm-1',
            confirmationId: 'confirm-1',
            toolCallId: 'tool-2',
            invocationId: 'invoke-2',
            toolName: 'shell_command',
            toolDisplayName: 'Shell Command',
            title: '执行命令',
            description: '需要确认执行参数',
            riskLevel: 'MEDIUM',
            editable: false,
            confirmLabel: '批准',
            denyLabel: '拒绝',
            displayFields: [],
            editableFields: [],
          },
        ],
        status: 'waiting-confirmation',
      },
    })

    expect(wrapper.find('.chat-pane__transcript').text()).toContain('是否继续？')
    expect(wrapper.find('.chat-pane__transcript').text()).toContain('执行命令')
  })
})
