import { mount } from '@vue/test-utils'
import TimelineSettingsPopover from '@/features/workspace/components/TimelineSettingsPopover.vue'

describe('TimelineSettingsPopover', () => {
  it('allows editing and emits save while the session has not started', async () => {
    const wrapper = mount(TimelineSettingsPopover, {
      props: {
        agents: [
          { agentKey: 'default_agent', name: 'Default Agent' },
          { agentKey: 'deer-flow', name: 'Deer Flow' },
        ],
        selectedAgentKey: 'default_agent',
        userId: 'demo-user',
        hasStarted: false,
      },
    })

    await wrapper.get('[data-testid="settings-agent-select"]').setValue('deer-flow')
    await wrapper.get('[data-testid="settings-user-id-input"]').setValue('workspace-user')
    await wrapper.get('[data-testid="settings-save"]').trigger('click')

    expect(wrapper.emitted('save')?.[0]).toEqual([
      { agentKey: 'deer-flow', userId: 'workspace-user' },
    ])
  })

  it('disables both fields and shows the locked hint after the session has started', () => {
    const wrapper = mount(TimelineSettingsPopover, {
      props: {
        agents: [{ agentKey: 'default_agent', name: 'Default Agent' }],
        selectedAgentKey: 'default_agent',
        userId: 'demo-user',
        hasStarted: true,
      },
    })

    expect(wrapper.get('[data-testid="settings-agent-select"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="settings-user-id-input"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('对话开始后不可修改智能体和用户 ID')
    expect(wrapper.find('[data-testid="settings-save"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="settings-close"]').text()).toContain('关闭')
  })
})
