import { createPinia, setActivePinia } from 'pinia'
import {
  createApexApiClient,
  SessionStateHttpError,
  setApexApiClientForTesting,
} from '@/services/apex-api'
import { useSessionStore } from '@/stores/session/store'
import type { ApexApiClient } from '@/services/apex-api'
import type { ChatRequest, HumanInterventionEnvelope, SseEnvelope } from '@/types/apex'

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

  it('逐卡回答或跳过后一次提交混合人工介入', async () => {
    let resumePayload: ChatRequest['humanResponse']
    let resumeRequests = 0
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async streamChat(request, _userId, _signal, onEnvelope) {
        if (request.type === 'NEW') {
          onEnvelope(humanInterventionEnvelope())
          onEnvelope({
            event_type: 'END',
            context: { mode: 'react', execution_status: 'HUMAN_IN_THE_LOOP' },
            messages: [],
          })
          return
        }

        resumeRequests += 1
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

    expect(store.session.status).toBe('waiting-intervention')
    expect(store.session.pendingInterventions).toHaveLength(3)
    const first = store.session.pendingInterventions[0]
    const skipped = store.session.pendingInterventions[1]
    const confirmation = store.session.pendingInterventions[2]
    if (first.kind !== 'question' || skipped.kind !== 'question'
      || confirmation.kind !== 'confirmation') {
      throw new Error('人工介入顺序不符合预期')
    }

    store.answerPrompt(first, 'react')
    store.skipIntervention(skipped)
    store.answerConfirmation(confirmation, 'APPROVE', { room: 'B2001' })
    expect(resumeRequests).toBe(0)
    await store.submitInterventions()

    expect(resumePayload).toEqual({
      'call-1': {
        interaction_type: 'ASK_HUMAN',
        answers: { '0': 'react' },
      },
      'call-2': {
        interaction_type: 'TOOL_CONFIRMATION',
        confirmation_id: 'confirm-2',
        decision: 'APPROVE',
        updated_args: { room: 'B2001' },
      },
    })
    expect(store.session.status).toBe('completed')
    expect(store.session.messages.at(-1)?.role).toBe('assistant')
  })

  it('全部跳过时提交空 humanResponse', async () => {
    let resumePayload: ChatRequest['humanResponse']
    const mockClient: ApexApiClient = {
      async fetchAgents() {
        return [{ agentKey: 'default_agent', name: 'Default Agent' }]
      },
      async streamChat(request, _userId, _signal, onEnvelope) {
        if (request.type === 'NEW') {
          onEnvelope(humanInterventionEnvelope())
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

    store.session.pendingInterventions.forEach((item) => {
      store.skipIntervention(item)
    })
    await store.submitInterventions()

    expect(resumePayload).toEqual({})
    expect(store.session.status).toBe('completed')
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

  it('刷新后恢复完整人工介入批次且不自动提交', async () => {
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
          pendingInteraction: humanInterventionEnvelope(),
        }
      },
      streamChat,
    }
    setActivePinia(createPinia())
    setApexApiClientForTesting(mockClient)

    const store = useSessionStore()
    await store.initialize()

    expect(store.session.sessionId).toBe('session-refresh')
    expect(store.session.status).toBe('waiting-intervention')
    expect(store.session.pendingInterventions.map((item) => item.toolCallId)).toEqual([
      'call-1', 'call-1', 'call-2',
    ])
    expect(streamChat).not.toHaveBeenCalled()
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

function humanInterventionEnvelope(): HumanInterventionEnvelope {
  return {
    event_type: 'HUMAN_INTERVENTION',
    context: { mode: 'react' },
    messages: [
      {
        interaction_type: 'ASK_HUMAN',
        tool_call_id: 'call-1',
        invocation_id: 'invocation-1',
        tool_name: 'ask_human',
        questions: [
          {
            input_type: 'SINGLE_SELECT',
            question: '请选择模式',
            options: [{ label: 'react' }, { label: 'plan-executor' }],
          },
          {
            input_type: 'TEXT_INPUT',
            question: '补充说明',
            options: [],
          },
        ],
      },
      {
        interaction_type: 'TOOL_CONFIRMATION',
        confirmation_id: 'confirm-2',
        tool_call_id: 'call-2',
        invocation_id: 'invocation-2',
        tool_name: 'meeting_tool',
        tool_display_name: '会议室助手',
        title: '预订会议室前确认',
        description: '请确认会议信息。',
        risk_level: 'MEDIUM',
        editable: true,
        confirm_label: '确认执行',
        deny_label: '取消',
        display_fields: [],
        editable_fields: [],
      },
    ],
  }
}
