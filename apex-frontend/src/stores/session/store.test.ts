import { createPinia, setActivePinia } from 'pinia'
import { createApexApiClient, setApexApiClientForTesting } from '@/services/apex-api'
import { useSessionStore } from '@/stores/session/store'
import type { ApexApiClient } from '@/services/apex-api'
import type { ChatRequest, SseEnvelope } from '@/types/apex'

describe('useSessionStore', () => {
  afterEach(() => {
    setApexApiClientForTesting(createApexApiClient())
  })

  it('builds a plan-executor session from streamed envelopes', async () => {
    const requests: ChatRequest[] = []
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async streamChat(request, _userId, _signal, onEnvelope) {
        requests.push(request)

        const envelopes: SseEnvelope[] = [
          {
            event_type: 'PLAN_DECLARED',
            context: { mode: 'plan-executor' },
            messages: [
              {
                stage_id: 'stage-1',
                stage_name: 'Collect context',
                description: 'Read docs',
                status: 'PENDING',
              },
            ],
          },
          {
            event_type: 'INVOCATION_DECLARED',
            context: { mode: 'plan-executor', stage_id: 'stage-1', executor: 'contacts_tool' },
            messages: [
              {
                invocation_id: 'invoke-1',
                name: 'Search contacts',
                invocation_type: 'search',
                content: '',
                click_effect: 'append',
                complete: false,
                render_type: 'markdown',
              },
            ],
          },
          {
            event_type: 'INVOCATION_CHANGE',
            context: { mode: 'plan-executor', stage_id: 'stage-1', executor: 'contacts_tool' },
            messages: [
              {
                invocation_id: 'invoke-1',
                change_type: 'CONTENT_APPEND',
                content: 'Found two records.',
                render_type: 'markdown',
              },
            ],
          },
          {
            event_type: 'ARTIFACT_DECLARED',
            context: { mode: 'plan-executor', stage_id: 'stage-1' },
            messages: [
              {
                scope: 'STAGE',
                data_type: 'markdown',
                source: 'knowledge-base',
                artifact_id: 'artifact-1',
                artifact_name: 'Report',
                artifact_type: 'document',
                content: 'Initial draft',
                complete: true,
              },
            ],
          },
          {
            event_type: 'END',
            context: { mode: 'plan-executor', stage_id: 'stage-1' },
            messages: [],
          },
        ]

        envelopes.forEach((envelope) => onEnvelope(envelope))
      },
    }

    setActivePinia(createPinia())
    setApexApiClientForTesting(mockClient)

    const store = useSessionStore()
    await store.initialize()
    await store.sendPrompt('Analyze the backend flow')

    expect(requests).toHaveLength(1)
    expect(store.session.status).toBe('completed')
    expect(store.session.messages.at(-1)?.role).toBe('assistant')
    expect(store.session.stages[0]?.invocations[0]?.content).toBe('Found two records.')
    expect(store.session.stages[0]?.artifacts[0]?.artifactName).toBe('Report')
  })

  it('resumes a human-in-the-loop session with grouped answers', async () => {
    let resumePayload: ChatRequest['humanResponse']
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async streamChat(request, _userId, _signal, onEnvelope) {
        if (request.type === 'NEW') {
          onEnvelope({
            event_type: 'ASK_HUMAN',
            context: { mode: 'react' },
            messages: [
              {
                input_type: 'CONFIRM',
                question: 'Continue?',
                tool_call_id: 'tool-9',
              },
            ],
          })
          onEnvelope({
            event_type: 'END',
            context: { mode: 'react' },
            messages: [],
          })
          return
        }

        resumePayload = request.humanResponse
        onEnvelope({
          event_type: 'STREAM_CONTENT',
          context: { mode: 'react', content_id: 'content-9' },
          messages: [{ content: 'Resumed with human approval.' }],
        })
        onEnvelope({
          event_type: 'END',
          context: { mode: 'react' },
          messages: [],
        })
      },
    }

    setActivePinia(createPinia())
    setApexApiClientForTesting(mockClient)

    const store = useSessionStore()
    await store.initialize()
    await store.sendPrompt('Check whether approval is required')

    expect(store.session.status).toBe('waiting-human')
    expect(store.session.pendingPrompts).toHaveLength(1)

    await store.answerPrompt(store.session.pendingPrompts[0], '确认')

    expect(resumePayload).toEqual({
      'tool-9': {
        answers: {
          '0': '确认',
        },
      },
    })
    expect(store.session.status).toBe('completed')
    expect(store.session.messages.at(-1)?.role).toBe('assistant')
  })
})
