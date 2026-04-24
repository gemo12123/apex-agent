import { fetchEventSource } from '@microsoft/fetch-event-source'
import { createApexApiClient } from '@/services/apex-api'
import type { ChatRequest } from '@/types/apex'

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
})
