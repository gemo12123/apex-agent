import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getApexApiClient } from '@/services/apex-api'
import { buildHumanResponsePayload, createPendingInterventionRecords } from '@/stores/session/reducer'
import type {
  AgentSummary,
  ChatRequest,
  HumanPromptRecord,
  PendingInterventionRecord,
  SseEnvelope,
  ToolConfirmationRecord,
} from '@/types/apex'
import {
  createConversationView,
  fromHistory,
  type ConversationBlock,
  type ConversationIteration,
  type ConversationTurn,
} from '@/features/conversation/conversation'

const USER_ID_STORAGE_KEY = 'apex:user-id'

function defaultUserId(): string {
  return localStorage.getItem(USER_ID_STORAGE_KEY) ?? import.meta.env.VITE_APEX_USER_ID ?? 'demo-user'
}

function parseJson(value: string | undefined): Record<string, unknown> {
  if (!value) return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {}
  } catch { return {} }
}

export const useConversationStore = defineStore('conversation', () => {
  const session = ref(createConversationView())
  const agents = ref<AgentSummary[]>([])
  const histories = ref<Awaited<ReturnType<NonNullable<ReturnType<typeof getApexApiClient>['fetchSessions']>>> | []>([])
  const selectedAgentKey = ref('default_agent')
  const userId = ref(defaultUserId())
  const errorMessage = ref('')
  const loadingHistory = ref(false)
  const activeController = ref<AbortController | null>(null)
  const hasStarted = computed(() => session.value.turns.length > 0)

  async function initialize(): Promise<void> {
    errorMessage.value = ''
    try {
      agents.value = await getApexApiClient().fetchAgents(userId.value)
      if (agents.value.length && !agents.value.some((item) => item.agentKey === selectedAgentKey.value)) {
        selectedAgentKey.value = agents.value[0].agentKey
      }
      await refreshHistory()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '初始化对话页失败。'
    }
  }

  async function refreshHistory(): Promise<void> {
    const client = getApexApiClient()
    if (!client.fetchSessions) return
    histories.value = await client.fetchSessions(userId.value)
  }

  async function loadHistory(sessionId: string): Promise<void> {
    const client = getApexApiClient()
    if (!client.fetchSessionHistory || loadingHistory.value) return
    stopStream()
    loadingHistory.value = true
    errorMessage.value = ''
    try {
      const history = await client.fetchSessionHistory(sessionId, userId.value)
      session.value = fromHistory(history)
      selectedAgentKey.value = history.agentKey
      if (history.executionStatus === 'HUMAN_IN_THE_LOOP' && client.fetchSessionState) {
        const state = await client.fetchSessionState(history.sessionId, history.agentKey, userId.value)
        if (state.pendingInteraction) {
          session.value.pendingInterventions = createPendingInterventionRecords(state.pendingInteraction)
        }
      }
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '加载历史会话失败。'
    } finally { loadingHistory.value = false }
  }

  function resetSession(): void {
    stopStream()
    session.value = createConversationView()
    errorMessage.value = ''
  }

  async function sendPrompt(query: string): Promise<void> {
    if (!query.trim() || session.value.status === 'streaming' || session.value.status === 'waiting-intervention') return
    const sessionId = session.value.sessionId ?? crypto.randomUUID()
    const no = (session.value.turns.at(-1)?.no ?? 0) + 1
    session.value.sessionId = sessionId
    session.value.agentKey = selectedAgentKey.value
    session.value.status = 'streaming'
    session.value.turns.push({ no, question: query.trim(), status: 'streaming', iterations: [] })
    await runChat({ sessionId, query: query.trim(), type: 'NEW', agentKey: selectedAgentKey.value })
    void refreshHistory()
  }

  function answerPrompt(prompt: HumanPromptRecord, answer: string | string[]): void {
    const target = session.value.pendingInterventions.find((item): item is HumanPromptRecord => item.id === prompt.id && item.kind === 'question')
    if (target) { target.resolution = 'answered'; target.answer = answer }
  }

  function answerConfirmation(confirmation: ToolConfirmationRecord, decision: 'APPROVE' | 'DENY', updatedArgs: Record<string, unknown> = {}): void {
    const target = session.value.pendingInterventions.find((item): item is ToolConfirmationRecord => item.id === confirmation.id && item.kind === 'confirmation')
    if (target) { target.resolution = 'answered'; target.decision = decision; target.updatedArgs = decision === 'APPROVE' && Object.keys(updatedArgs).length ? { ...updatedArgs } : undefined }
  }

  function skipIntervention(intervention: PendingInterventionRecord): void {
    const target = session.value.pendingInterventions.find((item) => item.id === intervention.id)
    if (target) { target.resolution = 'skipped' }
  }

  async function submitInterventions(): Promise<void> {
    if (!session.value.sessionId || session.value.pendingInterventions.some((item) => item.resolution === 'pending')) return
    const payload = buildHumanResponsePayload(session.value.pendingInterventions)
    session.value.pendingInterventions = []
    await runChat({ sessionId: session.value.sessionId, query: '', type: 'HUMAN_RESPONSE', agentKey: selectedAgentKey.value, humanResponse: payload })
    void refreshHistory()
  }

  function stopStream(): void {
    activeController.value?.abort()
    activeController.value = null
    if (session.value.status === 'streaming') session.value.status = 'aborted'
  }

  async function runChat(request: ChatRequest): Promise<void> {
    activeController.value?.abort()
    const controller = new AbortController()
    activeController.value = controller
    session.value.status = 'streaming'
    try {
      await getApexApiClient().streamChat(request, userId.value, controller.signal, applyEnvelope)
    } catch (error) {
      if (controller.signal.aborted) { session.value.status = 'aborted'; return }
      session.value.status = 'error'
      errorMessage.value = error instanceof Error ? error.message : '流式请求失败。'
    } finally {
      if (activeController.value === controller) activeController.value = null
    }
  }

  function applyEnvelope(envelope: SseEnvelope): void {
    const context = envelope.context ?? {}
    if (envelope.event_type === 'TURN_START') {
      const no = context.turn_no ?? session.value.turns.at(-1)?.no ?? 1
      let turn = session.value.turns.find((item) => item.no === no)
      if (!turn) { turn = { no, question: '', status: 'streaming', iterations: [] }; session.value.turns.push(turn) }
      turn.status = 'streaming'; session.value.status = 'streaming'; return
    }
    if (envelope.event_type === 'ITERATION_START') {
      const turn = currentTurn(context.turn_no)
      if (!turn) return
      const no = context.iteration_no ?? turn.iterations.length + 1
      if (!turn.iterations.some((item) => item.no === no)) turn.iterations.push({ no, resumed: Boolean(context.resumed), status: 'streaming', blocks: [] })
      session.value.status = 'streaming'; return
    }
    if (envelope.event_type === 'ITERATION_END') {
      const iteration = currentIteration(context.turn_no, context.iteration_no)
      if (iteration) iteration.status = 'completed'
      return
    }
    if (envelope.event_type === 'TURN_END') {
      const turn = currentTurn(context.turn_no)
      if (turn) turn.status = 'completed'
      session.value.status = 'completed'; return
    }
    if (envelope.event_type === 'STREAM_CONTENT' || envelope.event_type === 'STREAM_THINK') {
      const iteration = currentIteration()
      if (!iteration) return
      const type = envelope.event_type === 'STREAM_CONTENT' ? 'content' : 'think'
      const id = context.content_id ?? `${type}:${iteration.no}`
      const delta = envelope.messages[0]?.content ?? ''
      const existing = iteration.blocks.find((block): block is Extract<ConversationBlock, { type: 'content' | 'think' }> => (block.type === type) && block.id === id)
      if (existing) existing.content += delta
      else iteration.blocks.push({ type, id, content: delta, streaming: true })
      session.value.status = 'streaming'; return
    }
    if (envelope.event_type === 'INVOCATION_DECLARED') {
      const iteration = currentIteration()
      if (!iteration) return
      envelope.messages.forEach((message) => iteration.blocks.push({ type: 'tool', id: message.invocation_id, toolName: message.name, arguments: parseJson(message.content), status: message.complete ? 'COMPLETE' : 'RUNNING' }))
      return
    }
    if (envelope.event_type === 'INVOCATION_CHANGE') {
      envelope.messages.forEach((message) => {
        const tool = session.value.turns.flatMap((turn) => turn.iterations).flatMap((iteration) => iteration.blocks).find((block): block is Extract<ConversationBlock, { type: 'tool' }> => block.type === 'tool' && block.id === message.invocation_id)
        if (!tool) return
        if (message.change_type === 'CONTENT_APPEND') tool.result = `${tool.result ?? ''}${message.content ?? ''}`
        if (message.change_type === 'STATUS_CHANGE' && message.status) tool.status = message.status as Extract<ConversationBlock, { type: 'tool' }>['status']
      })
      return
    }
    if (envelope.event_type === 'HUMAN_INTERVENTION') {
      session.value.pendingInterventions = createPendingInterventionRecords(envelope)
      session.value.status = 'waiting-intervention'
      const iteration = currentIteration()
      if (iteration) iteration.status = 'waiting'
      return
    }
    if (envelope.event_type === 'TASK_ERROR') {
      const iteration = currentIteration()
      iteration?.blocks.push({ type: 'error', id: `error:${Date.now()}`, content: envelope.messages[0]?.message ?? 'Agent 执行失败。' })
      session.value.status = 'error'; return
    }
    if (envelope.event_type === 'END' && !context.execution_status) {
      if (session.value.status !== 'error') session.value.status = session.value.pendingInterventions.length ? 'waiting-intervention' : 'completed'
    }
  }

  function currentTurn(no?: number): ConversationTurn | undefined {
    return no === undefined ? session.value.turns.at(-1) : session.value.turns.find((item) => item.no === no)
  }
  function currentIteration(turnNo?: number, iterationNo?: number): ConversationIteration | undefined {
    const turn = currentTurn(turnNo)
    return iterationNo === undefined ? turn?.iterations.at(-1) : turn?.iterations.find((item) => item.no === iterationNo)
  }

  return { agents, answerConfirmation, answerPrompt, errorMessage, hasStarted, histories, initialize, loadHistory, loadingHistory, refreshHistory, resetSession, selectedAgentKey, sendPrompt, session, skipIntervention, stopStream, submitInterventions, userId }
})
