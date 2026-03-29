import type {
  AskHumanEnvelope,
  ArtifactDeclaredDetail,
  ArtifactRecord,
  EnvelopeContext,
  HumanPromptRecord,
  InvocationChangeDetail,
  InvocationDeclaredDetail,
  InvocationRecord,
  MessageRecord,
  PlanChangeMessage,
  StageRecord,
  SessionViewModel,
  SseEnvelope,
  UserMessageRecord,
} from '@/types/apex'

function createMessageId(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`
}

export function createSessionViewModel(): SessionViewModel {
  return {
    sessionId: null,
    agentKey: null,
    status: 'idle',
    currentMode: null,
    messages: [],
    stages: [],
    globalArtifacts: [],
    pendingPrompts: [],
  }
}

export function appendUserMessage(state: SessionViewModel, content: string): SessionViewModel {
  const nextMessage: UserMessageRecord = {
    id: createMessageId('user'),
    role: 'user',
    content,
  }

  return {
    ...state,
    messages: [...state.messages, nextMessage],
  }
}

export function startAssistantMessage(state: SessionViewModel): SessionViewModel {
  const nextMessage: MessageRecord = {
    id: createMessageId('assistant'),
    role: 'assistant',
    content: '',
    think: '',
    flows: [],
  }

  return {
    ...state,
    messages: [...state.messages, nextMessage],
  }
}

export function createHumanPromptRecords(envelope: AskHumanEnvelope): HumanPromptRecord[] {
  return envelope.messages.map((message, index) => ({
    id: `${message.tool_call_id}:${index}`,
    index,
    inputType: message.input_type,
    question: message.question,
    description: message.description,
    options: message.options ?? [],
    toolCallId: message.tool_call_id,
    answered: false,
  }))
}

export function buildHumanResponsePayload(
  prompts: HumanPromptRecord[],
): Record<string, { answers: Record<string, string | string[]> }> {
  const answeredPrompts = prompts.filter((prompt) => prompt.answered)
  if (answeredPrompts.length === 0) {
    return {}
  }

  return answeredPrompts.reduce<Record<string, { answers: Record<string, string | string[]> }>>(
    (accumulator, prompt) => {
      if (prompt.answer === undefined) {
        return accumulator
      }

      if (!accumulator[prompt.toolCallId]) {
        accumulator[prompt.toolCallId] = { answers: {} }
      }

      accumulator[prompt.toolCallId].answers[String(prompt.index)] = prompt.answer
      return accumulator
    },
    {},
  )
}

export function applyEnvelope(state: SessionViewModel, envelope: SseEnvelope): SessionViewModel {
  const context: EnvelopeContext = envelope.context ?? {}
  const nextState = cloneState(state)
  nextState.currentMode = context.mode ?? nextState.currentMode

  switch (envelope.event_type) {
    case 'STREAM_THINK':
      nextState.status = 'streaming'
      updateAssistantMessage(nextState, (message) => {
        message.think += envelope.messages[0]?.content ?? ''
      })
      return nextState
    case 'STREAM_CONTENT':
      nextState.status = 'streaming'
      updateAssistantMessage(nextState, (message) => {
        const content = envelope.messages[0]?.content ?? ''
        const flowId = context.content_id ?? createMessageId('content')
        message.content += content

        const existingFlow = message.flows.find((flow) => flow.id === flowId)
        if (existingFlow) {
          existingFlow.content += content
        } else {
          message.flows.push({
            id: flowId,
            type: 'text',
            content,
          })
        }
      })
      return nextState
    case 'PLAN_DECLARED':
      nextState.status = 'streaming'
      nextState.stages = envelope.messages.map((message) => {
        const existingStage = nextState.stages.find((stage) => stage.id === message.stage_id)
        return {
          id: message.stage_id,
          name: message.stage_name,
          description: message.description,
          status: message.status,
          invocations: existingStage?.invocations ?? [],
          artifacts: existingStage?.artifacts ?? [],
        }
      })
      return nextState
    case 'PLAN_CHANGE':
      nextState.status = 'streaming'
      envelope.messages.forEach((message) => applyPlanChange(nextState, message))
      return nextState
    case 'INVOCATION_DECLARED':
      nextState.status = 'streaming'
      envelope.messages.forEach((message) => {
        const stage = ensureStage(nextState, context.stage_id)
        const invocation = stage.invocations.find((item) => item.id === message.invocation_id)
        if (invocation) {
          Object.assign(invocation, createInvocationRecord(message, stage.id, context.executor))
          return
        }

        stage.invocations.push(createInvocationRecord(message, stage.id, context.executor))
      })
      return nextState
    case 'INVOCATION_CHANGE':
      nextState.status = 'streaming'
      envelope.messages.forEach((message) => {
        const invocation = findInvocation(nextState, message.invocation_id, context.stage_id)
        if (!invocation) {
          return
        }

        applyInvocationChange(invocation, message)
      })
      return nextState
    case 'ARTIFACT_DECLARED':
      nextState.status = 'streaming'
      envelope.messages.forEach((message) => {
        if (message.scope === 'GLOBAL') {
          upsertGlobalArtifact(nextState, message)
          return
        }

        const stage = ensureStage(nextState, context.stage_id)
        upsertStageArtifact(stage, message, context.stage_id)
      })
      return nextState
    case 'ARTIFACT_CHANGE':
      nextState.status = 'streaming'
      envelope.messages.forEach((message) => {
        if (message.scope === 'GLOBAL') {
          const artifact = nextState.globalArtifacts.find((item) => item.id === message.artifact_id)
          if (artifact) {
            artifact.content += message.content
          }
          return
        }

        const stage = ensureStage(nextState, context.stage_id)
        const artifact = stage.artifacts.find((item) => item.id === message.artifact_id)
        if (artifact) {
          artifact.content += message.content
        }
      })
      return nextState
    case 'ASK_HUMAN':
      nextState.pendingPrompts = createHumanPromptRecords(envelope)
      nextState.status = 'waiting-human'
      return nextState
    case 'END':
      nextState.status = nextState.pendingPrompts.some((prompt) => !prompt.answered)
        ? 'waiting-human'
        : 'completed'
      return nextState
    default:
      return nextState
  }
}

function cloneState(state: SessionViewModel): SessionViewModel {
  return {
    ...state,
    messages: state.messages.map((message) =>
      message.role === 'assistant'
        ? {
            ...message,
            flows: message.flows.map((flow) => ({ ...flow })),
          }
        : { ...message },
    ),
    stages: state.stages.map((stage) => ({
      ...stage,
      invocations: stage.invocations.map((invocation) => ({ ...invocation })),
      artifacts: stage.artifacts.map((artifact) => ({ ...artifact })),
    })),
    globalArtifacts: state.globalArtifacts.map((artifact) => ({ ...artifact })),
    pendingPrompts: state.pendingPrompts.map((prompt) => ({
      ...prompt,
      options: prompt.options.map((option) => ({ ...option })),
      answer: Array.isArray(prompt.answer) ? [...prompt.answer] : prompt.answer,
    })),
  }
}

function updateAssistantMessage(
  state: SessionViewModel,
  updater: (message: Extract<MessageRecord, { role: 'assistant' }>) => void,
): void {
  const lastMessage = [...state.messages].reverse().find((message) => message.role === 'assistant')
  if (!lastMessage || lastMessage.role !== 'assistant') {
    return
  }

  updater(lastMessage)
}

function ensureStage(state: SessionViewModel, stageId?: string): StageRecord {
  const resolvedStageId = stageId ?? 'react-stage'
  const existingStage = state.stages.find((stage) => stage.id === resolvedStageId)
  if (existingStage) {
    return existingStage
  }

  const stage: StageRecord = {
    id: resolvedStageId,
    name: resolvedStageId === 'react-stage' ? 'Live execution' : 'Execution stage',
    description: '',
    status: 'RUNNING',
    invocations: [],
    artifacts: [],
  }

  state.stages.push(stage)
  return stage
}

function applyPlanChange(state: SessionViewModel, message: PlanChangeMessage): void {
  if (message.change_type === 'STATUS_CHANGE' && message.stage_id) {
    const stage = ensureStage(state, message.stage_id)
    if (message.status) {
      stage.status = message.status
    }
    return
  }

  if (message.change_type === 'PLAN_CHANGE') {
    if (message.operation === 'ADD_STAGE' && message.new_stage_id) {
      const nextStage: StageRecord = {
        id: message.new_stage_id,
        name: message.stage_name ?? message.new_stage_id,
        description: message.description ?? '',
        status: message.status ?? 'PENDING',
        invocations: [],
        artifacts: [],
      }

      const targetIndex = state.stages.findIndex((stage) => stage.id === message.stage_id)
      if (targetIndex >= 0) {
        state.stages.splice(targetIndex + 1, 0, nextStage)
      } else {
        state.stages.push(nextStage)
      }
    }

    if (message.operation === 'DELETE_STAGE' && message.stage_id) {
      state.stages = state.stages.filter((stage) => stage.id !== message.stage_id)
    }

    if (message.operation === 'UPDATE_STAGE' && message.stage_id) {
      const stage = ensureStage(state, message.stage_id)
      if (message.stage_name) {
        stage.name = message.stage_name
      }
      if (message.description) {
        stage.description = message.description
      }
      if (message.status) {
        stage.status = message.status
      }
    }
  }
}

function createInvocationRecord(
  message: InvocationDeclaredDetail,
  stageId: string,
  executor?: string,
): InvocationRecord {
  return {
    id: message.invocation_id,
    stageId,
    name: message.name,
    invocationType: message.invocation_type,
    status: message.complete ? 'COMPLETE' : 'RUNNING',
    renderType: message.render_type ?? 'markdown',
    content: message.content ?? '',
    executor,
  }
}

function findInvocation(
  state: SessionViewModel,
  invocationId: string,
  stageId?: string,
): InvocationRecord | undefined {
  if (stageId) {
    return state.stages
      .find((stage) => stage.id === stageId)
      ?.invocations.find((invocation) => invocation.id === invocationId)
  }

  return state.stages
    .flatMap((stage) => stage.invocations)
    .find((invocation) => invocation.id === invocationId)
}

function applyInvocationChange(
  invocation: InvocationRecord,
  message: InvocationChangeDetail,
): void {
  if (message.change_type === 'STATUS_CHANGE' && message.status) {
    invocation.status = message.status as InvocationRecord['status']
  }

  if (message.change_type === 'CONTENT_APPEND' && message.content) {
    invocation.content += message.content
  }

  if (message.render_type) {
    invocation.renderType = message.render_type
  }
}

function upsertStageArtifact(
  stage: StageRecord,
  message: ArtifactDeclaredDetail,
  stageId?: string,
): void {
  const existingArtifact = stage.artifacts.find((artifact) => artifact.id === message.artifact_id)
  if (existingArtifact) {
    Object.assign(existingArtifact, createArtifactRecord(message, stageId))
    return
  }

  stage.artifacts.push(createArtifactRecord(message, stageId))
}

function upsertGlobalArtifact(
  state: SessionViewModel,
  message: ArtifactDeclaredDetail,
): void {
  const existingArtifact = state.globalArtifacts.find((artifact) => artifact.id === message.artifact_id)
  if (existingArtifact) {
    Object.assign(existingArtifact, createArtifactRecord(message))
    return
  }

  state.globalArtifacts.push(createArtifactRecord(message))
}

function createArtifactRecord(
  message: ArtifactDeclaredDetail,
  stageId?: string,
): ArtifactRecord {
  return {
    id: message.artifact_id,
    stageId,
    scope: message.scope === 'GLOBAL' ? 'GLOBAL' : 'STAGE',
    artifactName: message.artifact_name,
    artifactType: message.artifact_type,
    dataType: message.data_type,
    source: message.source,
    content: message.content,
    complete: message.complete,
  }
}
