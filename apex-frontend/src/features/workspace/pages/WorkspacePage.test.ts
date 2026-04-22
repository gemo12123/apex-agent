import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createApexApiClient, setApexApiClientForTesting } from '@/services/apex-api'
import WelcomeScreen from '@/features/workspace/components/WelcomeScreen.vue'
import WorkspacePage from '@/features/workspace/pages/WorkspacePage.vue'
import { useSessionStore } from '@/stores/session/store'
import type { ApexApiClient } from '@/services/apex-api'

describe('WorkspacePage', () => {
  afterEach(() => {
    setApexApiClientForTesting(createApexApiClient())
  })

  it('returns to the welcome screen and clears the current session when clicking back home', async () => {
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [
          { agentKey: 'default_agent', name: 'Default Agent' },
          { agentKey: 'deer-flow', name: 'Deer Flow' },
        ]
      },
      async streamChat() {},
    }

    const pinia = createPinia()
    setActivePinia(pinia)
    setApexApiClientForTesting(mockClient)

    const wrapper = mount(WorkspacePage, {
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const store = useSessionStore()
    store.setSelectedAgent('deer-flow')
    store.setUserId('workspace-user')
    store.session.sessionId = 'session-1'
    store.session.status = 'completed'
    store.session.messages = [
      {
        id: 'user-1',
        role: 'user',
        content: 'Analyze the runtime.',
      },
      {
        id: 'assistant-1',
        role: 'assistant',
        content: 'Here is the summary.',
        think: '',
        flows: [],
      },
    ]
    store.session.stages = [
      {
        id: 'stage-1',
        name: 'Collect context',
        description: 'Inspect docs',
        status: 'COMPLETE',
        invocations: [],
        artifacts: [],
      },
    ]

    await nextTick()

    await wrapper.get('[data-testid="back-home"]').trigger('click')
    await nextTick()

    expect(wrapper.findComponent(WelcomeScreen).exists()).toBe(true)
    expect(store.session.sessionId).toBeNull()
    expect(store.session.messages).toHaveLength(0)
    expect(store.session.stages).toHaveLength(0)
    expect(store.selectedAgentKey).toBe('deer-flow')
    expect(store.userId).toBe('workspace-user')
  })
})
