export type ChatRequestType = 'NEW' | 'HUMAN_RESPONSE'

export interface ChatRequest {
  sessionId: string
  query: string
  type: ChatRequestType
  agentKey: string
  humanResponse?: Record<string, HumanResponseEntry>
}

export interface AgentSummary {
  agentKey: string
  name: string
}

export interface SessionStateView {
  sessionId: string
  agentKey: string
  executionStatus: string
  pendingInteraction: HumanInterventionEnvelope | null
}

export interface SessionHistorySummary {
  sessionId: string
  agentKey: string
  sessionSummary: string | null
  executionStatus: string
  lastActiveTime: string
}

export interface ConversationHistoryBlock {
  type: 'content' | 'tool'
  id: string | null
  content: string | null
  toolName: string | null
  arguments: Record<string, unknown> | null
  resolvedArguments: Record<string, unknown> | null
  result: string | null
}

export interface ConversationHistoryIteration {
  no: number
  blocks: ConversationHistoryBlock[]
}

export interface ConversationHistoryTurn {
  no: number
  question: string
  iterations: ConversationHistoryIteration[]
}

export interface ConversationHistoryView {
  sessionId: string
  agentKey: string
  executionStatus: string
  turns: ConversationHistoryTurn[]
}

export interface EnvelopeContext {
  mode?: 'react' | 'plan-executor' | string
  stage_id?: string
  executor?: string
  content_id?: string
  invocation_id?: string
  execution_status?: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'HUMAN_IN_THE_LOOP' | string
  error_code?: string
  error_message?: string
  turn_no?: number
  iteration_no?: number
  resumed?: boolean
}

export interface StreamMessage {
  content: string
}

export interface TaskErrorDetail {
  message: string
}

export interface InvocationDeclaredDetail {
  invocation_id: string
  parent_invocation_id?: string
  name: string
  invocation_type: string
  click_effect?: string
  content?: string
  complete: boolean
  render_type?: string
}

export interface InvocationChangeDetail {
  change_type: 'STATUS_CHANGE' | 'CONTENT_APPEND'
  invocation_id: string
  status?: string
  content?: string
  render_type?: string
}

export interface ArtifactDeclaredDetail {
  scope: 'STAGE' | 'GLOBAL' | string
  data_type: string
  source?: string
  artifact_id: string
  artifact_name: string
  artifact_type: string
  content: string
  complete: boolean
}

export interface ArtifactChangeDetail {
  scope: 'STAGE' | 'GLOBAL' | string
  change_type: 'CONTENT_APPEND'
  source?: string
  artifact_id: string
  artifact_name: string
  artifact_type: string
  content: string
}

export interface AskHumanOption {
  label: string
  description?: string
}

export interface AskHumanQuestionDetail {
  input_type: 'TEXT_INPUT' | 'SINGLE_SELECT' | 'CONFIRM' | 'MULTI_SELECT'
  question: string
  description?: string
  options?: AskHumanOption[]
}

export interface AskHumanInterventionDetail {
  interaction_type: 'ASK_HUMAN'
  tool_call_id: string
  invocation_id: string
  tool_name: string
  questions: AskHumanQuestionDetail[]
}

export interface ToolConfirmationDisplayField {
  key: string
  label: string
  value: string | number | boolean | null
  type: 'text' | string
}

export interface ToolConfirmationEditableField {
  key: string
  label: string
  input_type: 'text' | 'textarea' | 'single-select' | 'confirm' | 'date' | 'datetime'
  value: string | number | boolean | null
  required?: boolean
  options?: AskHumanOption[]
}

export interface ToolConfirmationDetail {
  interaction_type: 'TOOL_CONFIRMATION'
  confirmation_id: string
  tool_call_id: string
  invocation_id: string
  tool_name: string
  tool_display_name: string
  title: string
  description?: string
  risk_level: string
  editable: boolean
  confirm_label: string
  deny_label: string
  display_fields: ToolConfirmationDisplayField[]
  editable_fields: ToolConfirmationEditableField[]
}

export interface SseEnvelopeBase<TType extends string, TMessages> {
  event_type: TType
  context: EnvelopeContext
  messages: TMessages[]
}

export type StreamThinkEnvelope = SseEnvelopeBase<'STREAM_THINK', StreamMessage>
export type StreamContentEnvelope = SseEnvelopeBase<'STREAM_CONTENT', StreamMessage>
export type InvocationDeclaredEnvelope = SseEnvelopeBase<'INVOCATION_DECLARED', InvocationDeclaredDetail>
export type InvocationChangeEnvelope = SseEnvelopeBase<'INVOCATION_CHANGE', InvocationChangeDetail>
export type ArtifactDeclaredEnvelope = SseEnvelopeBase<'ARTIFACT_DECLARED', ArtifactDeclaredDetail>
export type ArtifactChangeEnvelope = SseEnvelopeBase<'ARTIFACT_CHANGE', ArtifactChangeDetail>
export type TaskErrorEnvelope = SseEnvelopeBase<'TASK_ERROR', TaskErrorDetail>
export type HumanInterventionDetail = AskHumanInterventionDetail | ToolConfirmationDetail
export type HumanInterventionEnvelope = SseEnvelopeBase<
  'HUMAN_INTERVENTION',
  HumanInterventionDetail
>
export type EndEnvelope = SseEnvelopeBase<'END', never>
export type TurnStartEnvelope = SseEnvelopeBase<'TURN_START', never>
export type IterationStartEnvelope = SseEnvelopeBase<'ITERATION_START', never>
export type IterationEndEnvelope = SseEnvelopeBase<'ITERATION_END', never>
export type TurnEndEnvelope = SseEnvelopeBase<'TURN_END', never>

export type SseEnvelope =
  | StreamThinkEnvelope
  | StreamContentEnvelope
  | InvocationDeclaredEnvelope
  | InvocationChangeEnvelope
  | ArtifactDeclaredEnvelope
  | ArtifactChangeEnvelope
  | TaskErrorEnvelope
  | HumanInterventionEnvelope
  | TurnStartEnvelope
  | IterationStartEnvelope
  | IterationEndEnvelope
  | TurnEndEnvelope
  | EndEnvelope

export interface TextFlowRecord {
  id: string
  type: 'text'
  content: string
}

export interface InvocationRecord {
  id: string
  parentInvocationId?: string
  stageId: string
  name: string
  invocationType: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED' | 'CANCELLED'
  renderType: string
  content: string
  executor?: string
}

export interface ArtifactRecord {
  id: string
  stageId?: string
  scope: 'STAGE' | 'GLOBAL'
  artifactName: string
  artifactType: string
  dataType: string
  source?: string
  content: string
  complete: boolean
}

export interface HumanPromptRecord {
  id: string
  kind: 'question'
  index: number
  inputType: AskHumanQuestionDetail['input_type']
  question: string
  description?: string
  options: AskHumanOption[]
  toolCallId: string
  invocationId: string
  toolName: string
  resolution: 'pending' | 'answered' | 'skipped'
  answer?: string | string[]
}

export interface ToolConfirmationRecord {
  id: string
  kind: 'confirmation'
  confirmationId: string
  toolCallId: string
  invocationId: string
  toolName: string
  toolDisplayName: string
  title: string
  description?: string
  riskLevel: string
  editable: boolean
  confirmLabel: string
  denyLabel: string
  displayFields: ToolConfirmationDisplayField[]
  editableFields: ToolConfirmationEditableField[]
  resolution: 'pending' | 'answered' | 'skipped'
  decision?: 'APPROVE' | 'DENY'
  updatedArgs?: Record<string, unknown>
}

export type PendingInterventionRecord = HumanPromptRecord | ToolConfirmationRecord

export type HumanResponseEntry =
  | {
      interaction_type: 'ASK_HUMAN'
      answers: Record<string, string | string[]>
    }
  | {
      interaction_type: 'TOOL_CONFIRMATION'
      confirmation_id: string
      decision: 'APPROVE' | 'DENY'
      updated_args?: Record<string, unknown>
    }

export interface StageRecord {
  id: string
  name: string
  description: string
  status: string
  invocations: InvocationRecord[]
  artifacts: ArtifactRecord[]
}

export interface AssistantMessageRecord {
  id: string
  role: 'assistant'
  content: string
  think: string
  flows: TextFlowRecord[]
}

export interface UserMessageRecord {
  id: string
  role: 'user'
  content: string
}

export type MessageRecord = AssistantMessageRecord | UserMessageRecord

export interface SessionViewModel {
  sessionId: string | null
  agentKey: string | null
  status:
    | 'idle'
    | 'streaming'
    | 'waiting-intervention'
    | 'completed'
    | 'aborted'
    | 'error'
  currentMode: string | null
  messages: MessageRecord[]
  stages: StageRecord[]
  globalArtifacts: ArtifactRecord[]
  pendingInterventions: PendingInterventionRecord[]
}
