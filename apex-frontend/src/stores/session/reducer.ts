import type {
  ArtifactDeclaredDetail,
  ArtifactRecord,
  EnvelopeContext,
  HumanInterventionEnvelope,
  HumanPromptRecord,
  HumanResponseEntry,
  InvocationChangeDetail,
  InvocationDeclaredDetail,
  InvocationRecord,
  MessageRecord,
  PendingInterventionRecord,
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
    pendingInterventions: [],
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

export function createPendingInterventionRecords(
  envelope: HumanInterventionEnvelope,
): PendingInterventionRecord[] {
  return envelope.messages.flatMap<PendingInterventionRecord>(
    (message): PendingInterventionRecord[] => {
    if (message.interaction_type === 'ASK_HUMAN') {
      return message.questions.map<HumanPromptRecord>((question, index) => ({
        id: `${message.tool_call_id}:question:${index}`,
        kind: 'question',
        index,
        inputType: question.input_type,
        question: question.question,
        description: question.description,
        options: question.options ?? [],
        toolCallId: message.tool_call_id,
        invocationId: message.invocation_id,
        toolName: message.tool_name,
        resolution: 'pending',
      }))
    }

    return [{
      id: `${message.tool_call_id}:${message.confirmation_id}`,
      kind: 'confirmation' as const,
      confirmationId: message.confirmation_id,
      toolCallId: message.tool_call_id,
      invocationId: message.invocation_id,
      toolName: message.tool_name,
      toolDisplayName: message.tool_display_name,
      title: message.title,
      description: message.description,
      riskLevel: message.risk_level,
      editable: message.editable,
      confirmLabel: message.confirm_label,
      denyLabel: message.deny_label,
      displayFields: message.display_fields.map((field) => ({ ...field })),
      editableFields: message.editable_fields.map((field) => ({
        ...field,
        options: field.options?.map((option) => ({ ...option })),
      })),
      resolution: 'pending' as const,
    }]
    },
  )
}

export function buildHumanResponsePayload(
  interventions: PendingInterventionRecord[],
): Record<string, HumanResponseEntry> {
  return interventions.reduce<Record<string, HumanResponseEntry>>((payload, intervention) => {
    if (intervention.resolution !== 'answered') {
      return payload
    }

    if (intervention.kind === 'question') {
      if (intervention.answer === undefined) {
        return payload
      }
      const existing = payload[intervention.toolCallId]
      const answers = existing?.interaction_type === 'ASK_HUMAN' ? existing.answers : {}
      payload[intervention.toolCallId] = {
        interaction_type: 'ASK_HUMAN',
        answers: { ...answers, [String(intervention.index)]: intervention.answer },
      }
      return payload
    }

    if (!intervention.decision) {
      return payload
    }
    payload[intervention.toolCallId] = {
      interaction_type: 'TOOL_CONFIRMATION',
      confirmation_id: intervention.confirmationId,
      decision: intervention.decision,
      ...(intervention.updatedArgs ? { updated_args: intervention.updatedArgs } : {}),
    }
    return payload
  }, {})
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
    case 'HUMAN_INTERVENTION':
      nextState.pendingInterventions = createPendingInterventionRecords(envelope)
      nextState.status = 'waiting-intervention'
      return nextState
    case 'TASK_ERROR':
      nextState.status = 'error'
      return nextState
    case 'END':
      if (!context.execution_status) {
        if (nextState.status === 'error') {
          return nextState
        }
        nextState.status = nextState.pendingInterventions.length > 0
          ? 'waiting-intervention'
          : 'completed'
        return nextState
      }

      if (context.execution_status === 'FAILED') {
        nextState.status = 'error'
        return nextState
      }

      if (context.execution_status === 'HUMAN_IN_THE_LOOP') {
        nextState.status = 'waiting-intervention'
        return nextState
      }

      nextState.status = 'completed'
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
    pendingInterventions: state.pendingInterventions.map((intervention) =>
      intervention.kind === 'question'
        ? {
            ...intervention,
            options: intervention.options.map((option) => ({ ...option })),
            answer: Array.isArray(intervention.answer)
              ? [...intervention.answer]
              : intervention.answer,
          }
        : {
            ...intervention,
            displayFields: intervention.displayFields.map((field) => ({ ...field })),
            editableFields: intervention.editableFields.map((field) => ({
              ...field,
              options: field.options?.map((option) => ({ ...option })),
            })),
            updatedArgs: intervention.updatedArgs ? { ...intervention.updatedArgs } : undefined,
          },
    ),
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
    name: resolvedStageId === 'react-stage' ? '实时执行' : '执行阶段',
    description: '',
    status: 'RUNNING',
    invocations: [],
    artifacts: [],
  }

  state.stages.push(stage)
  return stage
}

function createInvocationRecord(
  message: InvocationDeclaredDetail,
  stageId: string,
  executor?: string,
): InvocationRecord {
  return {
    id: message.invocation_id,
    ...(message.parent_invocation_id
      ? { parentInvocationId: message.parent_invocation_id }
      : {}),
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
