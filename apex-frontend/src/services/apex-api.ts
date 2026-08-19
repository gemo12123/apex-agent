import { fetchEventSource } from '@microsoft/fetch-event-source'
import type { AgentSummary, ChatRequest, SessionStateView, SseEnvelope } from '@/types/apex'

interface ApiResponse<T> {
  code: number
  data: T
  message: string
}

export interface ApexApiClient {
  fetchAgents(userId: string): Promise<AgentSummary[]>
  fetchSessionState?(sessionId: string, agentKey: string, userId: string): Promise<SessionStateView>
  streamChat(
    request: ChatRequest,
    userId: string,
    signal: AbortSignal,
    onEnvelope: (envelope: SseEnvelope) => void,
  ): Promise<void>
}

const API_BASE = '/apex-api'

export class SessionStateHttpError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`加载会话状态失败（${status}）`)
    this.status = status
  }
}

function createHeaders(userId: string): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    'X-User-Id': userId,
  }
}

export function createApexApiClient(): ApexApiClient {
  return {
    async fetchAgents(userId) {
      const response = await fetch(`${API_BASE}/sse/agents`, {
        headers: createHeaders(userId),
      })

      if (!response.ok) {
        throw new Error(`加载代理列表失败（${response.status}）`)
      }

      const payload = (await response.json()) as ApiResponse<AgentSummary[]>
      return payload.data ?? []
    },
    async fetchSessionState(sessionId, agentKey, userId) {
      const response = await fetch(
        `${API_BASE}/sse/sessions/${encodeURIComponent(sessionId)}?agentKey=${encodeURIComponent(agentKey)}`,
        { headers: createHeaders(userId) },
      )

      if (!response.ok) {
        throw new SessionStateHttpError(response.status)
      }

      const payload = (await response.json()) as ApiResponse<SessionStateView>
      return payload.data
    },
    async streamChat(request, userId, signal, onEnvelope) {
      await fetchEventSource(`${API_BASE}/sse/chat`, {
        method: 'POST',
        headers: createHeaders(userId),
        body: JSON.stringify(request),
        openWhenHidden: true,
        signal,
        async onopen(response) {
          if (!response.ok) {
            throw new Error(`流式请求失败（${response.status}）`)
          }
        },
        onmessage(event) {
          if (!event.data) {
            return
          }

          const envelope = JSON.parse(event.data) as SseEnvelope
          onEnvelope(envelope)
        },
        onerror(error) {
          throw error
        },
      })
    },
  }
}

let activeApexApiClient: ApexApiClient = createApexApiClient()

export function getApexApiClient(): ApexApiClient {
  return activeApexApiClient
}

export function setApexApiClientForTesting(client: ApexApiClient): void {
  activeApexApiClient = client
}
