import {
  applyEnvelope,
  appendUserMessage,
  buildHumanResponsePayload,
  createSessionViewModel,
  startAssistantMessage,
} from './reducer'
import type { HumanInterventionEnvelope, SseEnvelope } from '@/types/apex'

describe('session reducer', () => {
  it('appends streaming text and think content onto the current assistant turn', () => {
    let state = createSessionViewModel()
    state = appendUserMessage(state, 'Summarize Apex.')
    state = startAssistantMessage(state)

    state = applyEnvelope(state, {
      event_type: 'STREAM_THINK',
      context: {},
      messages: [{ content: 'Inspecting the runtime model.' }],
    } satisfies SseEnvelope)

    state = applyEnvelope(state, {
      event_type: 'STREAM_CONTENT',
      context: { mode: 'react', content_id: 'content-1' },
      messages: [{ content: 'Apex streams standardized SSE events.' }],
    } satisfies SseEnvelope)

    state = applyEnvelope(state, {
      event_type: 'STREAM_CONTENT',
      context: { mode: 'react', content_id: 'content-1' },
      messages: [{ content: ' It also manages memory and tool orchestration.' }],
    } satisfies SseEnvelope)

    const assistantMessage = state.messages[state.messages.length - 1]
    expect(assistantMessage.role).toBe('assistant')
    if (assistantMessage.role !== 'assistant') {
      throw new Error('Expected assistant message')
    }

    expect(assistantMessage.think).toBe('Inspecting the runtime model.')
    expect(assistantMessage.content).toBe(
      'Apex streams standardized SSE events. It also manages memory and tool orchestration.',
    )
    expect(assistantMessage.flows).toEqual([
      {
        id: 'content-1',
        type: 'text',
        content:
          'Apex streams standardized SSE events. It also manages memory and tool orchestration.',
      },
    ])
    expect(state.status).toBe('streaming')
    expect(state.currentMode).toBe('react')
  })

  it('creates and updates stages, invocations, and stage artifacts using message IDs', () => {
    let state = createSessionViewModel()
    state = startAssistantMessage(state)

    state = applyEnvelope(state, {
      event_type: 'PLAN_DECLARED',
      context: { mode: 'plan-executor' },
      messages: [
        {
          stage_id: 'stage-1',
          stage_name: 'Collect context',
          description: 'Inspect backend contracts',
          status: 'PENDING',
        },
      ],
    } satisfies SseEnvelope)

    state = applyEnvelope(state, {
      event_type: 'INVOCATION_DECLARED',
      context: { mode: 'plan-executor', stage_id: 'stage-1', executor: 'meeting_tool' },
      messages: [
        {
          invocation_id: 'invoke-42',
          name: 'Query contacts',
          invocation_type: 'search',
          click_effect: 'append',
          content: '',
          complete: false,
          render_type: 'markdown',
        },
      ],
    } satisfies SseEnvelope)

    state = applyEnvelope(state, {
      event_type: 'INVOCATION_CHANGE',
      context: { mode: 'plan-executor', stage_id: 'stage-1', executor: 'meeting_tool' },
      messages: [
        {
          invocation_id: 'invoke-42',
          change_type: 'CONTENT_APPEND',
          content: 'Matched 3 contacts.',
          render_type: 'markdown',
        },
        {
          invocation_id: 'invoke-42',
          change_type: 'STATUS_CHANGE',
          status: 'COMPLETE',
        },
      ],
    } satisfies SseEnvelope)

    state = applyEnvelope(state, {
      event_type: 'ARTIFACT_DECLARED',
      context: { mode: 'plan-executor', stage_id: 'stage-1' },
      messages: [
        {
          scope: 'STAGE',
          data_type: 'markdown',
          source: 'knowledge-base',
          artifact_id: 'artifact-9',
          artifact_name: 'Runtime Notes',
          artifact_type: 'document',
          content: 'Baseline report',
          complete: false,
        },
      ],
    } satisfies SseEnvelope)

    state = applyEnvelope(state, {
      event_type: 'ARTIFACT_CHANGE',
      context: { mode: 'plan-executor', stage_id: 'stage-1' },
      messages: [
        {
          scope: 'STAGE',
          change_type: 'CONTENT_APPEND',
          source: 'knowledge-base',
          artifact_id: 'artifact-9',
          artifact_name: 'Runtime Notes',
          artifact_type: 'document',
          content: ' with execution details',
        },
      ],
    } satisfies SseEnvelope)

    const stage = state.stages[0]
    expect(stage).toMatchObject({
      id: 'stage-1',
      name: 'Collect context',
      description: 'Inspect backend contracts',
      status: 'PENDING',
    })
    expect(stage.invocations).toEqual([
      {
        id: 'invoke-42',
        stageId: 'stage-1',
        name: 'Query contacts',
        invocationType: 'search',
        status: 'COMPLETE',
        renderType: 'markdown',
        content: 'Matched 3 contacts.',
        executor: 'meeting_tool',
      },
    ])
    expect(stage.artifacts).toEqual([
      {
        id: 'artifact-9',
        stageId: 'stage-1',
        scope: 'STAGE',
        artifactName: 'Runtime Notes',
        artifactType: 'document',
        dataType: 'markdown',
        source: 'knowledge-base',
        content: 'Baseline report with execution details',
        complete: false,
      },
    ])
  })

  it('stores global artifacts separately from stage artifacts', () => {
    let state = createSessionViewModel()
    state = startAssistantMessage(state)

    state = applyEnvelope(state, {
      event_type: 'ARTIFACT_DECLARED',
      context: { mode: 'react' },
      messages: [
        {
          scope: 'GLOBAL',
          data_type: 'markdown',
          source: 'knowledge-base',
          artifact_id: 'artifact-global',
          artifact_name: 'Delivery Summary',
          artifact_type: 'document',
          content: 'Summary body',
          complete: true,
        },
      ],
    } satisfies SseEnvelope)

    expect(state.globalArtifacts).toEqual([
      {
        id: 'artifact-global',
        scope: 'GLOBAL',
        artifactName: 'Delivery Summary',
        artifactType: 'document',
        dataType: 'markdown',
        source: 'knowledge-base',
        content: 'Summary body',
        complete: true,
      },
    ])
  })

  it('按批量事件顺序展开混合卡片，并只聚合已回答项', () => {
    let state = createSessionViewModel()
    state = applyEnvelope(state, humanInterventionEnvelope())

    expect(state.pendingInterventions.map((item) => item.kind)).toEqual([
      'question',
      'question',
      'confirmation',
    ])
    expect(state.pendingInterventions.map((item) => item.toolCallId)).toEqual([
      'call-1',
      'call-1',
      'call-2',
    ])

    const firstQuestion = state.pendingInterventions[0]
    const skippedQuestion = state.pendingInterventions[1]
    const confirmation = state.pendingInterventions[2]
    if (firstQuestion.kind !== 'question'
      || skippedQuestion.kind !== 'question'
      || confirmation.kind !== 'confirmation') {
      throw new Error('人工介入卡片类型与顺序不符合预期')
    }
    firstQuestion.resolution = 'answered'
    firstQuestion.answer = 'react'
    skippedQuestion.resolution = 'skipped'
    confirmation.resolution = 'answered'
    confirmation.decision = 'APPROVE'
    confirmation.updatedArgs = { room: 'B2001' }

    expect(buildHumanResponsePayload(state.pendingInterventions)).toEqual({
      'call-1': {
        interaction_type: 'ASK_HUMAN',
        answers: { '0': 'react' },
      },
      'call-2': {
        interaction_type: 'TOOL_CONFIRMATION',
        confirmation_id: 'confirm-2',
        decision: 'APPROVE',
        updated_args: { room: 'B2001' },
      },
    })
  })

  it('全部跳过时生成空 humanResponse', () => {
    let state = createSessionViewModel()
    state = applyEnvelope(state, humanInterventionEnvelope())
    state.pendingInterventions.forEach((item) => {
      item.resolution = 'skipped'
    })

    expect(buildHumanResponsePayload(state.pendingInterventions)).toEqual({})
  })

  it('统一人工介入事件及其后的 END 都保持 waiting-intervention', () => {
    let waitingState = applyEnvelope(createSessionViewModel(), humanInterventionEnvelope())
    waitingState = applyEnvelope(waitingState, {
      event_type: 'END',
      context: { mode: 'react' },
      messages: [],
    } satisfies SseEnvelope)

    let completedState = startAssistantMessage(createSessionViewModel())
    completedState = applyEnvelope(completedState, {
      event_type: 'END',
      context: { mode: 'react' },
      messages: [],
    } satisfies SseEnvelope)

    expect(waitingState.status).toBe('waiting-intervention')
    expect(completedState.status).toBe('completed')
  })

  it('marks the session as error when END carries FAILED execution status', () => {
    let state = createSessionViewModel()
    state = startAssistantMessage(state)

    state = applyEnvelope(state, {
      event_type: 'END',
      context: {
        mode: 'react',
        execution_status: 'FAILED',
        error_code: 'STREAM_EXECUTION_FAILED',
        error_message: 'Model returned neither content nor tool calls',
      },
      messages: [],
    } satisfies SseEnvelope)

    expect(state.status).toBe('error')
  })

  it('END 携带 HUMAN_IN_THE_LOOP 时保持统一等待状态', () => {
    let state = applyEnvelope(createSessionViewModel(), humanInterventionEnvelope())

    state = applyEnvelope(state, {
      event_type: 'END',
      context: { mode: 'react', execution_status: 'HUMAN_IN_THE_LOOP' },
      messages: [],
    } satisfies SseEnvelope)

    expect(state.status).toBe('waiting-intervention')
  })
})

function humanInterventionEnvelope(): HumanInterventionEnvelope {
  return {
    event_type: 'HUMAN_INTERVENTION',
    context: { mode: 'react' },
    messages: [
      {
        interaction_type: 'ASK_HUMAN',
        tool_call_id: 'call-1',
        invocation_id: 'invocation-1',
        tool_name: 'ask_human',
        questions: [
          {
            input_type: 'SINGLE_SELECT',
            question: 'Pick an execution mode',
            description: 'This affects the next step',
            options: [{ label: 'react' }, { label: 'plan-executor' }],
          },
          {
            input_type: 'TEXT_INPUT',
            question: 'Any delivery notes?',
            options: [],
          },
        ],
      },
      {
        interaction_type: 'TOOL_CONFIRMATION',
        confirmation_id: 'confirm-2',
        tool_call_id: 'call-2',
        invocation_id: 'invocation-2',
        tool_name: 'meeting_tool',
        tool_display_name: '会议室助手',
        title: '预订会议室前确认',
        description: '请确认会议信息。',
        risk_level: 'MEDIUM',
        editable: true,
        confirm_label: '确认执行',
        deny_label: '取消',
        display_fields: [{ key: 'room', label: '会议室', value: 'A1001', type: 'text' }],
        editable_fields: [
          {
            key: 'room',
            label: '会议室',
            input_type: 'single-select',
            value: 'A1001',
            required: true,
            options: [{ label: 'A1001' }, { label: 'B2001' }],
          },
        ],
      },
    ],
  }
}
