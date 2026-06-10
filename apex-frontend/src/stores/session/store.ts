import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getApexApiClient } from '@/services/apex-api'
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
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '加载 Agent 列表失败。'
      agents.value = [{ agentKey: 'default_agent', name: '默认 Agent' }]
    } finally {
      isLoadingAgents.value = false
    }
  }

  function setUserId(nextUserId: string): void {
    const trimmedValue = nextUserId.trim() || 'demo-user'
    userId.value = trimmedValue
    localStorage.setItem(USER_ID_STORAGE_KEY, trimmedValue)
  }

  function setSelectedAgent(agentKey: string): void {
    selectedAgentKey.value = agentKey
  }

  function resetSession(): void {
    activeController.value?.abort()
    activeController.value = null
    errorMessage.value = ''
    session.value = createSessionViewModel()
  }

  async function sendPrompt(query: string): Promise<void> {
    if (!query.trim() || session.value.status === 'streaming') {
      return
    }

    const sessionId = session.value.sessionId ?? crypto.randomUUID()
    session.value.sessionId = sessionId

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
