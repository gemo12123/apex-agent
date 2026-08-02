import { createPinia, setActivePinia } from 'pinia'
import {
  createApexApiClient,
  SessionStateHttpError,
  setApexApiClientForTesting,
} from '@/services/apex-api'
import { useSessionStore } from '@/stores/session/store'
import type { ApexApiClient } from '@/services/apex-api'
import type { ChatRequest, SseEnvelope } from '@/types/apex'

describe('useSessionStore', () => {
  beforeEach(() => {
    localStorage.clear()
  })

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
            context: { mode: 'plan-executor', stage_id: 'stage-1', execution_status: 'COMPLETED' },
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
            context: { mode: 'react', execution_status: 'HUMAN_IN_THE_LOOP' },
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
          context: { mode: 'react', execution_status: 'COMPLETED' },
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
        interaction_type: 'ASK_HUMAN',
        answers: {
          '0': '确认',
        },
      },
    })
    expect(store.session.status).toBe('completed')
    expect(store.session.messages.at(-1)?.role).toBe('assistant')
  })

  it('resumes a tool confirmation session with edited arguments', async () => {
    let resumePayload: ChatRequest['humanResponse']
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async streamChat(request, _userId, _signal, onEnvelope) {
        if (request.type === 'NEW') {
          onEnvelope({
            event_type: 'TOOL_CONFIRMATION',
            context: { mode: 'react', executor: 'meeting_tool' },
            messages: [
              {
                confirmation_id: 'confirm-1',
                tool_call_id: 'call-1',
                invocation_id: 'invocation-1',
                tool_name: 'meeting_tool',
                tool_display_name: '会议室助手',
                title: '预订会议室前确认',
                description: '请确认会议信息。',
                risk_level: 'MEDIUM',
                editable: true,
                confirm_label: '确认执行',
                deny_label: '取消',
                display_fields: [{ key: 'room', label: '会议室', value: 'A1001', type: 'text' }],
                editable_fields: [
                  {
                    key: 'room',
                    label: '会议室',
                    input_type: 'single-select',
                    value: 'A1001',
                    required: true,
                    options: [{ label: 'A1001' }, { label: 'B2001' }],
                  },
                ],
              },
            ],
          })
          onEnvelope({
            event_type: 'END',
            context: { mode: 'react', execution_status: 'HUMAN_IN_THE_LOOP' },
            messages: [],
          })
          return
        }

        resumePayload = request.humanResponse
        onEnvelope({
          event_type: 'STREAM_CONTENT',
          context: { mode: 'react', content_id: 'content-confirmed' },
          messages: [{ content: 'Tool resumed after confirmation.' }],
        })
        onEnvelope({
          event_type: 'END',
          context: { mode: 'react', execution_status: 'COMPLETED' },
          messages: [],
        })
      },
    }

    setActivePinia(createPinia())
    setApexApiClientForTesting(mockClient)

    const store = useSessionStore()
    await store.initialize()
    await store.sendPrompt('Book the meeting room')

    expect(store.session.status).toBe('waiting-confirmation')
    expect(store.session.pendingConfirmations).toHaveLength(1)

    await store.submitConfirmation(store.session.pendingConfirmations[0], 'APPROVE', {
      room: 'B2001',
    })

    expect(resumePayload).toEqual({
      'call-1': {
        interaction_type: 'TOOL_CONFIRMATION',
        confirmation_id: 'confirm-1',
        decision: 'APPROVE',
        updated_args: {
          room: 'B2001',
        },
      },
    })
    expect(store.session.status).toBe('completed')
    expect(store.session.messages.at(-1)?.role).toBe('assistant')
  })

  it('marks the active session as error when the stream terminates with FAILED status', async () => {
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async streamChat(_request, _userId, _signal, onEnvelope) {
        const envelopes: SseEnvelope[] = [
          {
            event_type: 'STREAM_CONTENT',
            context: { mode: 'react', content_id: 'content-9' },
            messages: [{ content: 'hello' }],
          },
          {
            event_type: 'END',
            context: {
              mode: 'react',
              execution_status: 'FAILED',
              error_code: 'STREAM_EXECUTION_FAILED',
              error_message: 'boom',
            },
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
    await store.sendPrompt('Trigger failure')

    expect(store.session.status).toBe('error')
    expect(localStorage.getItem('apex:active-session:v1')).toBeNull()
  })

  it('restores a persisted ask-human interaction after refresh without submitting a response', async () => {
    localStorage.setItem('apex:active-session:v1', JSON.stringify({
      userId: 'demo-user',
      agentKey: 'default_agent',
      sessionId: 'session-refresh',
    }))
    const streamChat = vi.fn()
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async fetchSessionState() {
        return {
          sessionId: 'session-refresh',
          agentKey: 'default_agent',
          executionStatus: 'HUMAN_IN_THE_LOOP',
          pendingInteraction: {
            event_type: 'ASK_HUMAN',
            context: { mode: 'react', invocation_id: 'invocation-1' },
            messages: [{
              input_type: 'TEXT_INPUT',
              question: 'Continue?',
              tool_call_id: 'call-1',
            }],
          },
        }
      },
      streamChat,
    }
    setActivePinia(createPinia())
    setApexApiClientForTesting(mockClient)

    const store = useSessionStore()
    await store.initialize()

    expect(store.session.sessionId).toBe('session-refresh')
    expect(store.session.status).toBe('waiting-human')
    expect(store.session.pendingPrompts[0]?.toolCallId).toBe('call-1')
    expect(streamChat).not.toHaveBeenCalled()
  })

  it('restores a persisted tool confirmation after refresh', async () => {
    localStorage.setItem('apex:active-session:v1', JSON.stringify({
      userId: 'demo-user', agentKey: 'default_agent', sessionId: 'session-confirmation',
    }))
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async fetchSessionState() {
        return {
          sessionId: 'session-confirmation',
          agentKey: 'default_agent',
          executionStatus: 'HUMAN_IN_THE_LOOP',
          pendingInteraction: {
            event_type: 'TOOL_CONFIRMATION',
            context: { mode: 'react', invocation_id: 'invocation-1' },
            messages: [{
              confirmation_id: 'confirmation-1',
              tool_call_id: 'call-1',
              invocation_id: 'invocation-1',
              tool_name: 'search',
              tool_display_name: '搜索',
              title: '确认搜索',
              risk_level: 'MEDIUM',
              editable: false,
              confirm_label: '确认',
              deny_label: '拒绝',
              display_fields: [],
              editable_fields: [],
            }],
          },
        }
      },
      async streamChat() { },
    }
    setActivePinia(createPinia())
    setApexApiClientForTesting(mockClient)

    const store = useSessionStore()
    await store.initialize()

    expect(store.session.status).toBe('waiting-confirmation')
    expect(store.session.pendingConfirmations[0]?.confirmationId).toBe('confirmation-1')
  })

  it('clears stale locators on 404 but retains them on server errors', async () => {
    const locator = JSON.stringify({
      userId: 'demo-user', agentKey: 'default_agent', sessionId: 'session-refresh',
    })
    const client = (status: number): ApexApiClient => ({
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async fetchSessionState() { throw new SessionStateHttpError(status) },
      async streamChat() { },
    })

    localStorage.setItem('apex:active-session:v1', locator)
    setActivePinia(createPinia())
    setApexApiClientForTesting(client(404))
    await useSessionStore().initialize()
    expect(localStorage.getItem('apex:active-session:v1')).toBeNull()

    localStorage.setItem('apex:active-session:v1', locator)
    setActivePinia(createPinia())
    setApexApiClientForTesting(client(500))
    const store = useSessionStore()
    await store.initialize()
    expect(localStorage.getItem('apex:active-session:v1')).toBe(locator)
    expect(store.errorMessage).toContain('500')
  })
})
