import { fetchEventSource } from '@microsoft/fetch-event-source'
import { createApexApiClient } from '@/services/apex-api'
import type { ChatRequest, SseEnvelope } from '@/types/apex'

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(() => Promise.resolve()),
}))

describe('createApexApiClient', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('queries the read-only session state with owner headers', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      code: 200,
      data: {
        sessionId: 'session-1',
        agentKey: 'default_agent',
        executionStatus: 'COMPLETED',
        pendingInteraction: null,
      },
      message: 'success',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await createApexApiClient().fetchSessionState?.('session-1', 'default_agent', 'user-1')

    expect(fetchMock).toHaveBeenCalledWith(
      '/apex-api/sse/sessions/session-1?agentKey=default_agent',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-User-Id': 'user-1' }),
      }),
    )
  })

  it('keeps the chat stream open while the page is hidden', async () => {
    const apiClient = createApexApiClient()
    const request: ChatRequest = {
      sessionId: 'session-1',
      query: 'hello',
      type: 'NEW',
      agentKey: 'default_agent',
    }

    await apiClient.streamChat(request, 'demo-user', new AbortController().signal, vi.fn())

    expect(fetchEventSource).toHaveBeenCalledWith(
      '/apex-api/sse/chat',
      expect.objectContaining({
        openWhenHidden: true,
      }),
    )
  })

  it('parses TASK_ERROR SSE envelopes', async () => {
    vi.mocked(fetchEventSource).mockImplementationOnce(async (_url, options) => {
      options?.onmessage?.({
        data: JSON.stringify({
          event_type: 'TASK_ERROR',
          context: { mode: 'react' },
          messages: [{ message: 'boom' }],
        } satisfies SseEnvelope),
        event: 'message',
        id: 'event-1',
      })
    })

    const apiClient = createApexApiClient()
    const request: ChatRequest = {
      sessionId: 'session-1',
      query: 'hello',
      type: 'NEW',
      agentKey: 'default_agent',
    }
    const onEnvelope = vi.fn()

    await apiClient.streamChat(request, 'demo-user', new AbortController().signal, onEnvelope)

    expect(onEnvelope).toHaveBeenCalledWith(
      expect.objectContaining({
        event_type: 'TASK_ERROR',
        messages: [{ message: 'boom' }],
      }),
    )
  })
})
