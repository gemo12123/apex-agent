import {
  applyEnvelope,
  appendUserMessage,
  buildHumanResponsePayload,
  createSessionViewModel,
  startAssistantMessage,
} from './reducer'
import type { SseEnvelope } from '@/types/apex'

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

  it('builds ask-human prompts and resume payloads grouped by tool call id', () => {
    let state = createSessionViewModel()

    state = applyEnvelope(state, {
      event_type: 'ASK_HUMAN',
      context: { mode: 'react' },
      messages: [
        {
          input_type: 'SINGLE_SELECT',
          question: 'Pick an execution mode',
          description: 'This affects the next step',
          options: [
            { label: 'react', description: 'Step-by-step' },
            { label: 'plan-executor', description: 'Plan first' },
          ],
          tool_call_id: 'tool-call-1',
        },
        {
          input_type: 'TEXT_INPUT',
          question: 'Any delivery notes?',
          tool_call_id: 'tool-call-1',
        },
      ],
    } satisfies SseEnvelope)

    state.pendingPrompts[0].answered = true
    state.pendingPrompts[0].answer = 'react'
    state.pendingPrompts[1].answered = true
    state.pendingPrompts[1].answer = 'Keep the workspace responsive.'

    expect(state.pendingPrompts).toEqual([
      {
        id: 'tool-call-1:0',
        index: 0,
        inputType: 'SINGLE_SELECT',
        question: 'Pick an execution mode',
        description: 'This affects the next step',
        options: [
          { label: 'react', description: 'Step-by-step' },
          { label: 'plan-executor', description: 'Plan first' },
        ],
        toolCallId: 'tool-call-1',
        answered: true,
        answer: 'react',
      },
      {
        id: 'tool-call-1:1',
        index: 1,
        inputType: 'TEXT_INPUT',
        question: 'Any delivery notes?',
        description: undefined,
        options: [],
        toolCallId: 'tool-call-1',
        answered: true,
        answer: 'Keep the workspace responsive.',
      },
    ])

    expect(buildHumanResponsePayload(state.pendingPrompts)).toEqual({
      'tool-call-1': {
        answers: {
          '0': 'react',
          '1': 'Keep the workspace responsive.',
        },
      },
    })
  })

  it('marks the session as waiting-human or completed when the stream ends', () => {
    let waitingState = createSessionViewModel()
    waitingState = applyEnvelope(waitingState, {
      event_type: 'ASK_HUMAN',
      context: { mode: 'react' },
      messages: [
        {
          input_type: 'CONFIRM',
          question: 'Continue?',
          tool_call_id: 'confirm-1',
        },
      ],
    } satisfies SseEnvelope)

    waitingState = applyEnvelope(waitingState, {
      event_type: 'END',
      context: { mode: 'react' },
      messages: [],
    } satisfies SseEnvelope)

    let completedState = createSessionViewModel()
    completedState = startAssistantMessage(completedState)
    completedState = applyEnvelope(completedState, {
      event_type: 'END',
      context: { mode: 'react' },
      messages: [],
    } satisfies SseEnvelope)

    expect(waitingState.status).toBe('waiting-human')
    expect(completedState.status).toBe('completed')
  })
})
