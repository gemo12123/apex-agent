import type {
  ConversationHistoryView,
  PendingInterventionRecord,
} from '@/types/apex'

export type ConversationStatus = 'idle' | 'streaming' | 'waiting-intervention' | 'completed' | 'aborted' | 'error'

export type ConversationBlock =
  | { type: 'content'; id: string; content: string; streaming?: boolean }
  | { type: 'think'; id: string; content: string; streaming?: boolean }
  | {
      type: 'tool'
      id: string
      toolName: string
      arguments: Record<string, unknown>
      resolvedArguments?: Record<string, unknown>
      result?: string
      status: 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED' | 'CANCELLED'
    }
  | { type: 'error'; id: string; content: string }

export interface ConversationIteration {
  no: number
  resumed: boolean
  status: 'streaming' | 'completed' | 'waiting' | 'error' | 'aborted'
  blocks: ConversationBlock[]
}

export interface ConversationTurn {
  no: number
  question: string
  status: ConversationStatus
  iterations: ConversationIteration[]
}

export interface ConversationViewModel {
  sessionId: string | null
  agentKey: string | null
  status: ConversationStatus
  turns: ConversationTurn[]
  pendingInterventions: PendingInterventionRecord[]
}

export function createConversationView(): ConversationViewModel {
  return { sessionId: null, agentKey: null, status: 'idle', turns: [], pendingInterventions: [] }
}

export function fromHistory(history: ConversationHistoryView): ConversationViewModel {
  return {
    sessionId: history.sessionId,
    agentKey: history.agentKey,
    status: history.executionStatus === 'HUMAN_IN_THE_LOOP'
      ? 'waiting-intervention'
      : history.executionStatus === 'FAILED' ? 'error' : history.executionStatus === 'CANCELLED' ? 'aborted' : 'completed',
    pendingInterventions: [],
    turns: history.turns.map((turn) => ({
      no: turn.no,
      question: turn.question,
      status: 'completed',
      iterations: turn.iterations.map((iteration) => ({
        no: iteration.no,
        resumed: false,
        status: 'completed',
        blocks: iteration.blocks.map((block, index) => block.type === 'content'
          ? { type: 'content', id: `history:${turn.no}:${iteration.no}:${index}`, content: block.content ?? '' }
          : {
              type: 'tool',
              id: block.id ?? `history:${turn.no}:${iteration.no}:${index}`,
              toolName: block.toolName ?? '未知工具',
              arguments: block.arguments ?? {},
              ...(block.resolvedArguments ? { resolvedArguments: block.resolvedArguments } : {}),
              ...(block.result ? { result: block.result } : {}),
              status: block.result ? 'COMPLETE' : 'PENDING',
            }),
      })),
    })),
  }
}

export function sameJson(left: Record<string, unknown>, right: Record<string, unknown>): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}
