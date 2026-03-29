import { mount } from '@vue/test-utils'
import WelcomeScreen from '@/features/workspace/components/WelcomeScreen.vue'

describe('WelcomeScreen', () => {
  it('emits submit and configuration updates', async () => {
    const wrapper = mount(WelcomeScreen, {
      props: {
        agents: [
          { agentKey: 'default_agent', name: 'Default Agent' },
          { agentKey: 'deer-flow', name: 'Deer Flow' },
        ],
        selectedAgentKey: 'default_agent',
        userId: 'demo-user',
        loading: false,
        errorMessage: '',
      },
    })

    await wrapper.get('textarea').setValue('Inspect the SSE contract')
    await wrapper.get('select').setValue('deer-flow')
    await wrapper.get('input').setValue('workspace-user')
    await wrapper.get('.welcome-screen__submit').trigger('click')

    expect(wrapper.emitted('update:selectedAgentKey')?.[0]).toEqual(['deer-flow'])
    expect(wrapper.emitted('update:userId')?.[0]).toEqual(['workspace-user'])
    expect(wrapper.emitted('submit')?.[0]).toEqual(['Inspect the SSE contract'])
  })
})
