export type ChatRequestType = 'NEW' | 'HUMAN_RESPONSE'

export interface ChatRequest {
  sessionId: string
  query: string
  type: ChatRequestType
  agentKey: string
  humanResponse?: Record<string, { answers: Record<string, string | string[]> }>
}

export interface AgentSummary {
  agentKey: string
  name: string
}

export interface EnvelopeContext {
  mode?: 'react' | 'plan-executor' | string
  stage_id?: string
  executor?: string
  content_id?: string
}

export interface StreamMessage {
  content: string
}

export interface PlanMessage {
  stage_id: string
  stage_name: string
  description: string
  status: string
}

export interface PlanChangeMessage {
  change_type: 'STATUS_CHANGE' | 'PLAN_CHANGE' | 'TRY_REPLAN'
  stage_id?: string
  status?: string
  operation?: 'ADD_STAGE' | 'DELETE_STAGE' | 'UPDATE_STAGE'
  stage_name?: string
  description?: string
  new_stage_id?: string
}

export interface InvocationDeclaredDetail {
  invocation_id: string
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

export interface AskHumanDetail {
  input_type: 'TEXT_INPUT' | 'SINGLE_SELECT' | 'CONFIRM' | 'MULTI_SELECT'
  question: string
  description?: string
  options?: AskHumanOption[]
  tool_call_id: string
}

export interface SseEnvelopeBase<TType extends string, TMessages> {
  event_type: TType
  context: EnvelopeContext
  messages: TMessages[]
}

export type StreamThinkEnvelope = SseEnvelopeBase<'STREAM_THINK', StreamMessage>
export type StreamContentEnvelope = SseEnvelopeBase<'STREAM_CONTENT', StreamMessage>
export type PlanDeclaredEnvelope = SseEnvelopeBase<'PLAN_DECLARED', PlanMessage>
export type PlanChangeEnvelope = SseEnvelopeBase<'PLAN_CHANGE', PlanChangeMessage>
export type InvocationDeclaredEnvelope = SseEnvelopeBase<'INVOCATION_DECLARED', InvocationDeclaredDetail>
export type InvocationChangeEnvelope = SseEnvelopeBase<'INVOCATION_CHANGE', InvocationChangeDetail>
export type ArtifactDeclaredEnvelope = SseEnvelopeBase<'ARTIFACT_DECLARED', ArtifactDeclaredDetail>
export type ArtifactChangeEnvelope = SseEnvelopeBase<'ARTIFACT_CHANGE', ArtifactChangeDetail>
export type AskHumanEnvelope = SseEnvelopeBase<'ASK_HUMAN', AskHumanDetail>
export type EndEnvelope = SseEnvelopeBase<'END', never>

export type SseEnvelope =
  | StreamThinkEnvelope
  | StreamContentEnvelope
  | PlanDeclaredEnvelope
  | PlanChangeEnvelope
  | InvocationDeclaredEnvelope
  | InvocationChangeEnvelope
  | ArtifactDeclaredEnvelope
  | ArtifactChangeEnvelope
  | AskHumanEnvelope
  | EndEnvelope

export interface TextFlowRecord {
  id: string
  type: 'text'
  content: string
}

export interface InvocationRecord {
  id: string
  stageId: string
  name: string
  invocationType: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED'
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
  index: number
  inputType: AskHumanDetail['input_type']
  question: string
  description?: string
  options: AskHumanOption[]
  toolCallId: string
  answered: boolean
  answer?: string | string[]
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
  status: 'idle' | 'streaming' | 'waiting-human' | 'completed' | 'aborted' | 'error'
  currentMode: string | null
  messages: MessageRecord[]
  stages: StageRecord[]
  globalArtifacts: ArtifactRecord[]
  pendingPrompts: HumanPromptRecord[]
}
