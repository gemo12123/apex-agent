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
  ArtifactRecord,
  ChatRequest,
  HumanPromptRecord,
  InvocationRecord,
} from '@/types/apex'

const USER_ID_STORAGE_KEY = 'apex:user-id'

type DetailSelection =
  | { kind: 'invocation'; stageId: string; id: string }
  | { kind: 'artifact'; scope: 'STAGE' | 'GLOBAL'; stageId?: string; id: string }
  | null

function defaultUserId(): string {
  return localStorage.getItem(USER_ID_STORAGE_KEY) ?? import.meta.env.VITE_APEX_USER_ID ?? 'demo-user'
}

export const useSessionStore = defineStore('session', () => {
  const session = ref(createSessionViewModel())
  const agents = ref<AgentSummary[]>([])
  const selectedAgentKey = ref('default_agent')
  const userId = ref(defaultUserId())
  const selection = ref<DetailSelection>(null)
  const isLoadingAgents = ref(false)
  const errorMessage = ref('')
  const activeController = ref<AbortController | null>(null)

  const hasStarted = computed(() =>
    session.value.messages.some((message) => message.role === 'user'),
  )

  const selectedInvocation = computed<InvocationRecord | null>(() => {
    if (!selection.value || selection.value.kind !== 'invocation') {
      return null
    }

    return (
      session.value.stages
        .find((stage) => stage.id === selection.value?.stageId)
        ?.invocations.find((invocation) => invocation.id === selection.value?.id) ?? null
    )
  })

  const selectedArtifact = computed<ArtifactRecord | null>(() => {
    if (!selection.value || selection.value.kind !== 'artifact') {
      return null
    }

    if (selection.value.scope === 'GLOBAL') {
      return session.value.globalArtifacts.find((artifact) => artifact.id === selection.value?.id) ?? null
    }

    return (
      session.value.stages
        .find((stage) => stage.id === selection.value?.stageId)
        ?.artifacts.find((artifact) => artifact.id === selection.value?.id) ?? null
    )
  })

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
      errorMessage.value = error instanceof Error ? error.message : 'Failed to load agents.'
      agents.value = [{ agentKey: 'default_agent', name: 'Default Agent' }]
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

  function stopStream(): void {
    activeController.value?.abort()
    activeController.value = null
    session.value.status = 'aborted'
  }

  function selectInvocation(stageId: string, invocationId: string): void {
    selection.value = { kind: 'invocation', stageId, id: invocationId }
  }

  function selectArtifact(scope: 'STAGE' | 'GLOBAL', artifactId: string, stageId?: string): void {
    selection.value = { kind: 'artifact', scope, stageId, id: artifactId }
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
      errorMessage.value = error instanceof Error ? error.message : 'The stream failed.'
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
    selectedAgentKey,
    selectedArtifact,
    selectedInvocation,
    selectArtifact,
    selectInvocation,
    sendPrompt,
    session,
    setSelectedAgent,
    setUserId,
    stopStream,
    userId,
  }
})
