import { fetchEventSource } from '@microsoft/fetch-event-source'
import { createApexApiClient } from '@/services/apex-api'
import type { ChatRequest, SseEnvelope } from '@/types/apex'

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(() => Promise.resolve()),
}))

describe('createApexApiClient', () => {
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

  it('parses terminal SSE envelopes with explicit execution status', async () => {
    vi.mocked(fetchEventSource).mockImplementationOnce(async (_url, options) => {
      options?.onmessage?.({
        data: JSON.stringify({
          event_type: 'END',
          context: {
            mode: 'react',
            execution_status: 'FAILED',
            error_code: 'STREAM_EXECUTION_FAILED',
            error_message: 'boom',
          },
          messages: [],
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
        event_type: 'END',
        context: expect.objectContaining({
          execution_status: 'FAILED',
          error_code: 'STREAM_EXECUTION_FAILED',
        }),
      }),
    )
  })
})
