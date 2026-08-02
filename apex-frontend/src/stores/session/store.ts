import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getApexApiClient, SessionStateHttpError } from '@/services/apex-api'
import {
  applyEnvelope,
  appendUserMessage,
  buildHumanResponsePayload,
  createSessionViewModel,
  startAssistantMessage,
} from '@/stores/session/reducer'
import type {
  AgentSummary,
  ChatRequest,
  HumanPromptRecord,
  ToolConfirmationRecord,
} from '@/types/apex'

const USER_ID_STORAGE_KEY = 'apex:user-id'
const ACTIVE_SESSION_STORAGE_KEY = 'apex:active-session:v1'

interface ActiveSessionLocator {
  userId: string
  agentKey: string
  sessionId: string
}

function readActiveSession(): ActiveSessionLocator | null {
  const raw = localStorage.getItem(ACTIVE_SESSION_STORAGE_KEY)
  if (!raw) return null
  try {
    const value = JSON.parse(raw) as Partial<ActiveSessionLocator>
    if (!value.userId?.trim() || !value.agentKey?.trim() || !value.sessionId?.trim()) {
      localStorage.removeItem(ACTIVE_SESSION_STORAGE_KEY)
      return null
    }
    return value as ActiveSessionLocator
  } catch {
    localStorage.removeItem(ACTIVE_SESSION_STORAGE_KEY)
    return null
  }
}

function saveActiveSession(locator: ActiveSessionLocator): void {
  localStorage.setItem(ACTIVE_SESSION_STORAGE_KEY, JSON.stringify(locator))
}

function clearActiveSession(): void {
  localStorage.removeItem(ACTIVE_SESSION_STORAGE_KEY)
}

function shouldRetainActiveSession(status: string): boolean {
  return status === 'waiting-human' || status === 'waiting-confirmation'
}

function defaultUserId(): string {
  return localStorage.getItem(USER_ID_STORAGE_KEY) ?? import.meta.env.VITE_APEX_USER_ID ?? 'demo-user'
}

export const useSessionStore = defineStore('session', () => {
  const session = ref(createSessionViewModel())
  const agents = ref<AgentSummary[]>([])
  const selectedAgentKey = ref('default_agent')
  const userId = ref(defaultUserId())
  const isLoadingAgents = ref(false)
  const errorMessage = ref('')
  const activeController = ref<AbortController | null>(null)

  const hasStarted = computed(() =>
    session.value.messages.some((message) => message.role === 'user'),
  )

  async function initialize(): Promise<void> {
    if (isLoadingAgents.value) {
      return
    }

    isLoadingAgents.value = true
    errorMessage.value = ''

    try {
      const nextAgents = await getApexApiClient().fetchAgents(userId.value)
      agents.value = nextAgents

      if (nextAgents.length > 0) {
        selectedAgentKey.value = nextAgents.some((agent) => agent.agentKey === selectedAgentKey.value)
          ? selectedAgentKey.value
          : nextAgents[0].agentKey
      }

      const locator = readActiveSession()
      if (!locator) return
      if (locator.userId !== userId.value
        || !nextAgents.some((agent) => agent.agentKey === locator.agentKey)) {
        clearActiveSession()
        return
      }
      const client = getApexApiClient()
      if (!client.fetchSessionState) return
      try {
        const state = await client.fetchSessionState(locator.sessionId, locator.agentKey, locator.userId)
        if (state.executionStatus !== 'HUMAN_IN_THE_LOOP' || !state.pendingInteraction) {
          clearActiveSession()
          return
        }
        selectedAgentKey.value = locator.agentKey
        session.value = createSessionViewModel()
        session.value.sessionId = locator.sessionId
        session.value.agentKey = locator.agentKey
        session.value = applyEnvelope(session.value, state.pendingInteraction)
      } catch (error) {
        if (error instanceof SessionStateHttpError && error.status === 404) {
          clearActiveSession()
          return
        }
        errorMessage.value = error instanceof Error ? error.message : '加载会话状态失败。'
      }
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '加载 Agent 列表失败。'
      agents.value = [{ agentKey: 'default_agent', name: '默认 Agent' }]
    } finally {
      isLoadingAgents.value = false
    }
  }

  function setUserId(nextUserId: string): void {
    const trimmedValue = nextUserId.trim() || 'demo-user'
    if (trimmedValue !== userId.value) {
      clearActiveSession()
      resetSession()
    }
    userId.value = trimmedValue
    localStorage.setItem(USER_ID_STORAGE_KEY, trimmedValue)
  }

  function setSelectedAgent(agentKey: string): void {
    if (agentKey !== selectedAgentKey.value) {
      clearActiveSession()
    }
    selectedAgentKey.value = agentKey
  }

  function resetSession(): void {
    activeController.value?.abort()
    activeController.value = null
    errorMessage.value = ''
    session.value = createSessionViewModel()
    clearActiveSession()
  }

  async function sendPrompt(query: string): Promise<void> {
    if (!query.trim() || session.value.status === 'streaming') {
      return
    }

    const sessionId = session.value.sessionId ?? crypto.randomUUID()
    session.value.sessionId = sessionId
    session.value.agentKey = selectedAgentKey.value
    saveActiveSession({ userId: userId.value, agentKey: selectedAgentKey.value, sessionId })

    errorMessage.value = ''
    session.value = appendUserMessage(session.value, query.trim())
    session.value = startAssistantMessage(session.value)
    session.value.pendingPrompts = []
    session.value.pendingConfirmations = []

    await runChat({
      sessionId,
      query: query.trim(),
      type: 'NEW',
      agentKey: selectedAgentKey.value,
    })
  }

  async function answerPrompt(prompt: HumanPromptRecord, answer: string | string[]): Promise<void> {
    const targetPrompt = session.value.pendingPrompts.find((item) => item.id === prompt.id)
    if (!targetPrompt) {
      return
    }

    targetPrompt.answered = true
    targetPrompt.answer = answer

    if (!session.value.pendingPrompts.every((item) => item.answered)) {
      return
    }

    const payload = buildHumanResponsePayload(session.value.pendingPrompts)
    const sessionId = session.value.sessionId ?? crypto.randomUUID()
    session.value.sessionId = sessionId
    session.value.pendingPrompts = []
    errorMessage.value = ''

    await runChat({
      sessionId,
      query: '',
      type: 'HUMAN_RESPONSE',
      agentKey: selectedAgentKey.value,
      humanResponse: payload,
    })
  }

  async function submitConfirmation(
    confirmation: ToolConfirmationRecord,
    decision: 'APPROVE' | 'DENY',
    updatedArgs: Record<string, unknown> = {},
  ): Promise<void> {
    const sessionId = session.value.sessionId ?? crypto.randomUUID()
    session.value.sessionId = sessionId
    session.value.pendingConfirmations = []
    errorMessage.value = ''

    await runChat({
      sessionId,
      query: '',
      type: 'HUMAN_RESPONSE',
      agentKey: selectedAgentKey.value,
      humanResponse: {
        [confirmation.toolCallId]: {
          interaction_type: 'TOOL_CONFIRMATION',
          confirmation_id: confirmation.confirmationId,
          decision,
          ...(decision === 'APPROVE' && Object.keys(updatedArgs).length > 0
            ? { updated_args: updatedArgs }
            : {}),
        },
      },
    })
  }

  function stopStream(): void {
    activeController.value?.abort()
    activeController.value = null
    session.value.status = 'aborted'
    clearActiveSession()
  }

  async function runChat(request: ChatRequest): Promise<void> {
    activeController.value?.abort()
    const controller = new AbortController()
    activeController.value = controller
    session.value.status = 'streaming'

    try {
      await getApexApiClient().streamChat(request, userId.value, controller.signal, (envelope) => {
        session.value = applyEnvelope(session.value, envelope)
      })
    } catch (error) {
      if (controller.signal.aborted) {
        session.value.status = 'aborted'
        return
      }

      session.value.status = 'error'
      errorMessage.value = error instanceof Error ? error.message : '流式请求失败。'
    } finally {
      if (activeController.value === controller) {
        activeController.value = null
      }
      if (shouldRetainActiveSession(session.value.status)) {
        if (session.value.sessionId) {
          saveActiveSession({
            userId: userId.value,
            agentKey: selectedAgentKey.value,
            sessionId: session.value.sessionId,
          })
        }
      } else {
        clearActiveSession()
      }
    }
  }

  return {
    agents,
    answerPrompt,
    errorMessage,
    hasStarted,
    initialize,
    isLoadingAgents,
    resetSession,
    selectedAgentKey,
    sendPrompt,
    session,
    setSelectedAgent,
    setUserId,
    stopStream,
    submitConfirmation,
    userId,
  }
})
