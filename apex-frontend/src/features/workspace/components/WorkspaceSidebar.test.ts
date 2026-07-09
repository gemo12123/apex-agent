import { mount } from '@vue/test-utils'
import WorkspaceSidebar from '@/features/workspace/components/WorkspaceSidebar.vue'

describe('WorkspaceSidebar', () => {
  it('renders the settings trigger in the sidebar and saves session settings from the popover', async () => {
    const wrapper = mount(WorkspaceSidebar, {
      props: {
        agents: [
          { agentKey: 'default_agent', name: 'Default Agent' },
          { agentKey: 'deer-flow', name: 'Deer Flow' },
        ],
        selectedAgentKey: 'default_agent',
        userId: 'demo-user',
        hasStarted: false,
        historyItems: [],
      },
    })

    expect(wrapper.get('[data-testid="new-chat"]').text()).toContain('新建会话')
    expect(wrapper.get('[data-testid="sidebar-history"]').text()).toContain('历史对话')
    expect(wrapper.get('[data-testid="sidebar-settings-trigger"]').text()).toContain('设置')

    await wrapper.get('[data-testid="new-chat"]').trigger('click')
    await wrapper.get('[data-testid="sidebar-settings-trigger"]').trigger('click')
    await wrapper.get('[data-testid="settings-agent-select"]').setValue('deer-flow')
    await wrapper.get('[data-testid="settings-user-id-input"]').setValue('workspace-user')
    await wrapper.get('[data-testid="settings-save"]').trigger('click')

    expect(wrapper.emitted('new-chat')).toHaveLength(1)
    expect(wrapper.emitted('save-settings')?.[0]).toEqual([
      { agentKey: 'deer-flow', userId: 'workspace-user' },
    ])
  })

  it('keeps the sidebar settings popover readable but locked once the conversation has started', async () => {
    const wrapper = mount(WorkspaceSidebar, {
      props: {
        agents: [{ agentKey: 'default_agent', name: 'Default Agent' }],
        selectedAgentKey: 'default_agent',
        userId: 'demo-user',
        hasStarted: true,
        historyItems: [],
      },
    })

    await wrapper.get('[data-testid="sidebar-settings-trigger"]').trigger('click')

    expect(wrapper.get('[data-testid="settings-agent-select"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="settings-user-id-input"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('对话开始后不可修改智能体和用户 ID')
  })

  it('closes the sidebar settings popover when clicking outside', async () => {
    const wrapper = mount(WorkspaceSidebar, {
      attachTo: document.body,
      props: {
        agents: [{ agentKey: 'default_agent', name: 'Default Agent' }],
        selectedAgentKey: 'default_agent',
        userId: 'demo-user',
        hasStarted: false,
        historyItems: [],
      },
    })

    await wrapper.get('[data-testid="sidebar-settings-trigger"]').trigger('click')
    expect(wrapper.find('[data-testid="sidebar-settings-popover"]').exists()).toBe(true)

    document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="sidebar-settings-popover"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
