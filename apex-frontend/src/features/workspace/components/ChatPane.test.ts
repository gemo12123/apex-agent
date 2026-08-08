import { mount } from '@vue/test-utils'
import ChatPane from '@/features/workspace/components/ChatPane.vue'

describe('ChatPane', () => {
  it('renders the empty state above the shared composer and fills the draft from suggestions', async () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: false,
        messages: [],
        pendingInterventions: [],
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
        pendingInterventions: [],
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

  it('disables the shared composer while waiting for intervention', () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: true,
        messages: [],
        pendingInterventions: [],
        status: 'waiting-intervention',
      },
    })

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="send-button"]').attributes('disabled')).toBeDefined()
  })

  it('按统一批次顺序渲染问题和工具确认', async () => {
    const wrapper = mount(ChatPane, {
      props: {
        hasStarted: true,
        messages: [],
        pendingInterventions: [
          {
            id: 'prompt-1',
            kind: 'question',
            index: 0,
            toolCallId: 'tool-1',
            invocationId: 'invoke-1',
            toolName: 'ask_human',
            inputType: 'CONFIRM',
            question: '是否继续？',
            description: '需要人工确认',
            options: [],
            resolution: 'pending',
          },
          {
            id: 'confirm-1',
            kind: 'confirmation',
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
            resolution: 'pending',
          },
        ],
        status: 'waiting-intervention',
      },
    })

    const transcript = wrapper.find('.chat-pane__transcript').text()
    expect(transcript.indexOf('是否继续？')).toBeLessThan(transcript.indexOf('执行命令'))
    expect(wrapper.get('[data-testid="submit-interventions"]').attributes('disabled')).toBeDefined()

    await wrapper.setProps({
      pendingInterventions: wrapper.props('pendingInterventions').map((item, index) => ({
        ...item,
        resolution: index === 0 ? 'answered' as const : 'skipped' as const,
      })),
    })
    expect(wrapper.get('[data-testid="submit-interventions"]').attributes('disabled')).toBeUndefined()
  })
})
