import { mount } from '@vue/test-utils'
import WorkspaceSidebar from '@/features/workspace/components/WorkspaceSidebar.vue'

describe('WorkspaceSidebar', () => {
  it('places the agent selector next to the new chat action and keeps user id in the footer', async () => {
    const wrapper = mount(WorkspaceSidebar, {
      props: {
        agents: [
          { agentKey: 'default_agent', name: 'Default Agent' },
          { agentKey: 'deer-flow', name: 'Deer Flow' },
        ],
        selectedAgentKey: 'default_agent',
        userId: 'demo-user',
        historyItems: [],
      },
    })

    expect(wrapper.get('[data-testid="sidebar-history"]').text()).toContain('历史对话')
    expect(wrapper.get('[data-testid="toggle-user-settings"]').text()).toContain('用户 ID')
    expect(wrapper.text()).not.toContain('规划任务')
    expect(wrapper.text()).not.toContain('最近产物')
    expect(wrapper.text()).not.toContain('待确认')

    await wrapper.get('[data-testid="new-chat"]').trigger('click')
    await wrapper.get('[data-testid="agent-select"]').setValue('deer-flow')
    await wrapper.get('[data-testid="toggle-user-settings"]').trigger('click')
    await wrapper.get('[data-testid="user-id-input"]').setValue('workspace-user')

    expect(wrapper.emitted('new-chat')).toHaveLength(1)
    expect(wrapper.emitted('update:selectedAgentKey')?.[0]).toEqual(['deer-flow'])
    expect(wrapper.emitted('update:userId')?.[0]).toEqual(['workspace-user'])
  })
})
