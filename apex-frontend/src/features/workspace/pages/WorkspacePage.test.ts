import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createApexApiClient, setApexApiClientForTesting } from '@/services/apex-api'
import WorkspacePage from '@/features/workspace/pages/WorkspacePage.vue'
import { useSessionStore } from '@/stores/session/store'
import type { ApexApiClient } from '@/services/apex-api'

describe('WorkspacePage', () => {
  afterEach(() => {
    setApexApiClientForTesting(createApexApiClient())
  })

  it('keeps the centered main column stable while toggling the timeline drawer', async () => {
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

    expect(wrapper.find('.workspace-page__main-column').exists()).toBe(true)
    expect(wrapper.get('main').classes()).not.toContain('workspace-page--timeline-open')

    await wrapper.get('[data-testid="toggle-timeline"]').trigger('click')

    expect(wrapper.get('main').classes()).toContain('workspace-page--timeline-open')
    expect(wrapper.find('.workspace-page__main-column').exists()).toBe(true)
  })

  it('opens the timeline drawer from the main pane and returns to the welcome state on new chat', async () => {
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
    store.session.sessionId = 'session-1'
    store.session.status = 'completed'
    store.session.messages = [
      {
        id: 'user-1',
        role: 'user',
        content: '总结执行流程',
      },
      {
        id: 'assistant-1',
        role: 'assistant',
        content: '好的',
        think: '',
        flows: [],
      },
    ]
    store.session.stages = [
      {
        id: 'stage-1',
        name: '收集上下文',
        description: '读取 docs',
        status: 'COMPLETE',
        invocations: [],
        artifacts: [],
      },
    ]

    await nextTick()

    await wrapper.get('[data-testid="toggle-timeline"]').trigger('click')
    expect(wrapper.find('[data-testid="timeline-entry-stage:stage-1"]').exists()).toBe(true)

    await wrapper.get('[data-testid="new-chat"]').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('今天想让 Apex 做什么？')
    expect(store.session.sessionId).toBeNull()
    expect(store.session.messages).toHaveLength(0)
    expect(store.session.stages).toHaveLength(0)
  })
})
