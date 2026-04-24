# Agent Hook Runtime Design

Date: 2026-04-22
Status: Approved for planning

## 1. Background

`apex-agent` already has the following building blocks:

- agent-level global configuration in `application.yml`
- per-agent workspace configuration in `agents/<agentKey>/config.yml`
- a tool execution pipeline centered around `CustomToolCallingManager`
- streaming runtime status over SSE
- `ASK_HUMAN` plus `HUMAN_RESPONSE` session suspension and resumption
- a frontend execution rail and interactive cards

However, the current system does not yet provide a formal hook runtime around tool invocation. Existing behavior is still notification-oriented:

- `ToolInvocationNotifier` can observe before/after execution
- `ASK_HUMAN` can suspend for generic human input
- tool results are written back directly without a formal post-processing seam

The target capability is a configurable, agent-scoped hook runtime that can:

- run before and after tool invocation
- access tool name and tool arguments
- mutate tool arguments before execution
- request explicit human confirmation with a dedicated confirmation card
- resume the suspended tool call without asking the model to regenerate it
- rewrite or truncate tool results after execution

## 2. Goals

- Support `PRE_TOOL_CALL` and `POST_TOOL_CALL` as first-class hook events.
- Allow each agent to declare its own hooks.
- Support two configuration sources:
  - `application.yml`
  - `agents/<agentKey>/config.yml`
- Allow workspace `config.yml` to override global hook configuration.
- Treat `hooks: []` in workspace config as an explicit disable for all hooks of that agent.
- Execute hook logic through Spring-managed Java beans.
- Add a dedicated `TOOL_CONFIRMATION` SSE event instead of reusing `ASK_HUMAN`.
- Support confirmation cards with hook-defined Chinese display text and editable form fields.
- Support post-hook result replacement for use cases such as truncation.

## 3. Non-Goals for This Version

- script-based hooks
- `PRE_LLM_CALL` and `POST_LLM_CALL`
- subagent, memory, or session lifecycle hooks
- generic JSON editor for tool parameters
- hook management UI
- hook CLI diagnostics such as `list` or `doctor`
- parallel hook execution

## 4. Gap Summary

Compared with the reference projects studied under `want_learn/`:

- `claude-code-main` already has a formal hook event model and can mutate tool input and output. `apex-agent` does not yet have a dedicated hook runtime contract.
- `hermes-agent-main` already separates observational tool hooks from result transformation seams. `apex-agent` still writes tool results back directly.
- `claw-code-main` treats hooks as an explicit subsystem. `apex-agent` still has hook-like behavior spread across notifier and human-input paths.

The missing capabilities in `apex-agent` are:

- a hook configuration model
- a hook dispatch runtime
- a pre-hook mutation contract
- a post-hook result transformation contract
- a dedicated tool confirmation event and restore path
- persisted suspended tool execution state

## 5. Chosen Approach

Use an independent `AgentHookRuntime` instead of extending `ToolInvocationNotifier` into a general-purpose orchestration layer.

Reasoning:

- `ToolInvocationNotifier` is currently notification-oriented and too narrow for mutation, blocking, and resumption.
- `AgentWorkspaceService` already owns the merge pattern for global config plus workspace config.
- `CustomToolCallingManager` is already the narrowest point where every tool call passes through.
- `SuperAgent` plus `HumanInLoopResumer` already establish a suspend/resume conversation lifecycle that can be extended.

## 6. Configuration Model

### 6.1 Global agent config

Extend `AgentConfig` with a `hooks` field.

Suggested shape in `application.yml`:

```yaml
apex:
  global:
    agents:
      default_agent:
        hooks:
          pre-tool-call:
            - bean: toolConfirmHook
              enabled: true
              order: 100
              tools: ["meeting_tool", "contacts_tool"]
              options:
                title: "工具调用确认"
          post-tool-call:
            - bean: truncateLongResultHook
              enabled: true
              order: 200
              tools: ["*"]
              options:
                max-length: 4000
                append-notice: true
```

### 6.2 Workspace agent config

Extend `agents/<agentKey>/config.yml` with a sibling `hooks` block.

Example:

```yaml
default-execution-mode: REACT
allow-mcps: ["meeting-server"]
hooks:
  pre-tool-call:
    - bean: toolConfirmHook
      enabled: true
      tools: ["meeting_tool"]
      options:
        title: "会议室调用确认"
  post-tool-call: []
```

To explicitly disable all hooks for a workspace agent, the parser should also accept:

```yaml
hooks: []
```

### 6.3 Merge semantics

Configuration resolution will remain inside `AgentWorkspaceService`.

Rules:

- If workspace `config.yml` does not contain `hooks`, fall back to global agent hooks from `application.yml`.
- If workspace `config.yml` contains `hooks: []`, the agent has no hooks.
- If workspace `config.yml` contains an explicit empty hook structure, the agent also has no hooks.
- If workspace `config.yml` contains a non-empty `hooks` structure, workspace hooks fully replace global hooks for that agent.

This intentionally differs from the current `allow-mcps` behavior because hooks require explicit disable semantics.

## 7. Runtime Interfaces

### 7.1 Hook bean interfaces

```java
public interface PreToolCallHook {
    PreToolCallHookResult apply(PreToolCallHookContext context);
}

public interface PostToolCallHook {
    PostToolCallHookResult apply(PostToolCallHookContext context);
}
```

### 7.2 Pre-hook context

`PreToolCallHookContext` should include at least:

- `agentKey`
- `sessionId`
- `userId`
- `toolCallId`
- `invocationId`
- `toolName`
- `toolDescription`
- `toolType`
- `rawArguments`
- `arguments`
- `hookOptions`
- `SuperAgentContext`

### 7.3 Post-hook context

`PostToolCallHookContext` extends the same execution metadata with:

- `originalResult`
- `currentResult`

### 7.4 Pre-hook result contract

Supported outcomes:

- `proceed()`
- `proceedWithUpdatedArgs(Map<String, Object> updatedArgs)`
- `block(String reason)`
- `requestConfirmation(ToolConfirmationSpec spec)`

Pre-hooks execute sequentially in ascending `order`.

Rules:

- later hooks receive already-mutated args from earlier hooks
- `block` short-circuits the pipeline
- `requestConfirmation` short-circuits the pipeline and suspends the tool call

### 7.5 Post-hook result contract

Supported outcomes:

- `keep()`
- `replaceResult(String nextResult)`

Post-hooks also execute sequentially in ascending `order`.

Each hook receives the latest `currentResult`.

## 8. Hook Runtime

Introduce `AgentHookRuntime` as the sole entry point for tool hook orchestration.

Responsibilities:

- resolve effective hook configuration for an agent
- match configured hooks by event and tool name
- instantiate hooks through Spring bean lookup
- execute the pre-hook or post-hook pipeline
- return structured decisions to the caller

Recommended collaborators:

- `AgentWorkspaceService` for merged config
- `ApplicationContext` for bean resolution
- a dedicated matcher utility for `tools: ["*", "meeting_tool"]`

## 9. Tool Execution Flow

### 9.1 Normal flow

1. Model emits tool call.
2. `CustomToolCallingManager` resolves `toolName`, `toolCallId`, and parsed arguments.
3. `AgentHookRuntime.runPreHooks(...)` executes.
4. If pre-hooks return updated arguments, the tool executes with those arguments.
5. Tool returns the raw result.
6. `AgentHookRuntime.runPostHooks(...)` executes.
7. Final result is written into `ToolResponseMessage`.

### 9.2 Blocked flow

1. Model emits tool call.
2. A pre-hook returns `block(reason)`.
3. The real tool is not executed.
4. A synthetic `ToolResponseMessage` is produced so the model sees the block reason as the tool response.

### 9.3 Confirmation flow

1. Model emits tool call.
2. A pre-hook returns `requestConfirmation(spec)`.
3. Runtime sends `TOOL_CONFIRMATION` SSE message.
4. Runtime marks the invocation as waiting for confirmation.
5. Session enters `HUMAN_IN_THE_LOOP`.
6. On resume, the original suspended tool call is restored and executed without asking the model to regenerate it.

## 10. Suspended Tool Execution State

The current `pendingToolResult` field only supports `ASK_HUMAN` style resume, where a tool response is appended back into the conversation.

`TOOL_CONFIRMATION` needs a different restore model because the suspended operation is a real tool invocation that has not executed yet.

Add persisted runtime state:

- `PendingHumanInteraction`
- `PendingToolExecution`

`PendingToolExecution` should include:

- `toolCallId`
- `toolName`
- `invocationId`
- `resolvedArguments`
- `editableFieldKeys`
- `confirmationId`
- `hookSource`

Both `SuperAgentContext` and `SessionRuntimeSnapshot` should be extended so this state survives both in-memory and JDBC stores.

## 11. Confirmation Card Contract

### 11.1 New SSE event

Add `TOOL_CONFIRMATION` to `AgentEventType` and `AgentMessage` polymorphism.

The payload is hook-driven rather than frontend-hardcoded.

Example:

```json
{
  "event_type": "TOOL_CONFIRMATION",
  "context": {
    "mode": "react",
    "stage_id": "react-stage",
    "executor": "meeting_tool"
  },
  "messages": [
    {
      "confirmation_id": "confirm-123",
      "tool_call_id": "call-1",
      "invocation_id": "invocationId_example",
      "tool_name": "meeting_tool",
      "tool_display_name": "会议室助手",
      "title": "预订会议室前确认",
      "description": "将使用会议室助手查询空闲时间并尝试提交预订。",
      "risk_level": "MEDIUM",
      "hook_source": "toolConfirmHook",
      "editable": true,
      "confirm_label": "确认执行",
      "deny_label": "取消",
      "display_fields": [
        { "key": "room", "label": "会议室", "value": "A1001", "type": "text" },
        { "key": "date", "label": "日期", "value": "2026-04-22", "type": "text" }
      ],
      "editable_fields": [
        { "key": "room", "label": "会议室", "input_type": "single-select", "value": "A1001", "required": true }
      ]
    }
  ]
}
```

### 11.2 ToolConfirmationSpec

`ToolConfirmationSpec` should include:

- `confirmationId`
- `title`
- `description`
- `toolDisplayName`
- `toolName`
- `riskLevel`
- `editable`
- `confirmLabel`
- `denyLabel`
- `displayFields`
- `editableFields`

### 11.3 Editable field types

First version supports only:

- `text`
- `textarea`
- `single-select`
- `confirm`
- `date`
- `datetime`

Not supported in this version:

- arbitrary JSON editors
- nested object editors
- list or array editors

## 12. Resume Request Contract

Keep `RequestType.HUMAN_RESPONSE`, but generalize the payload.

Suggested logical shape:

```json
{
  "call-1": {
    "interaction_type": "TOOL_CONFIRMATION",
    "confirmation_id": "confirm-123",
    "decision": "APPROVE",
    "updated_args": {
      "room": "B2001"
    }
  }
}
```

Ask-human remains supported:

```json
{
  "call-2": {
    "interaction_type": "ASK_HUMAN",
    "answers": {
      "0": "确认"
    }
  }
}
```

## 13. Resume Semantics

### 13.1 ASK_HUMAN

Retain the existing behavior:

- append a `ToolResponseMessage`
- continue the agent loop

### 13.2 TOOL_CONFIRMATION

New behavior:

- do not synthesize a tool response before execution
- restore `PendingToolExecution`
- if decision is `DENY`, create a synthetic tool response describing user cancellation
- if decision is `APPROVE`, merge `updated_args` into suspended arguments using editable field whitelist rules
- execute the real tool
- continue the loop after tool completion

Final argument precedence:

1. model original args
2. pre-hook-updated args
3. user-edited confirmation args

Only fields declared in `editableFields` may be overridden by the user.

## 14. Frontend Changes

### 14.1 Session state

Extend `SessionViewModel`:

- `pendingConfirmations: ToolConfirmationRecord[]`
- status gains `waiting-confirmation`

### 14.2 New component

Add `ToolConfirmationCard.vue`.

Recommended UX:

- show summary by default
- expose an explicit `编辑参数` button
- expand a form only when the user chooses to edit
- on submit, send `decision=APPROVE` and `updated_args`
- on cancel, send `decision=DENY`

### 14.3 Execution rail

Introduce a waiting state for invocations:

- `WAITING_CONFIRMATION`

When a tool call is suspended for approval:

- the chat pane shows the confirmation card
- the execution rail shows the corresponding invocation as waiting for confirmation

## 15. Post-Tool Result Replacement

The first version should support post-hook result replacement, including truncation, but should remain conservative.

Recommended built-in hook:

- `PlainTextTruncateHook`

Guidelines:

- plain text tool output may be truncated safely
- structured JSON should not be truncated blindly by default
- JSON-envelope replacement should only be used by explicitly opted-in hooks

Example plain text replacement:

```text
<preview text>

...[truncated by post-hook: truncateLongResultHook, original_length=12345]
```

## 16. Testing Strategy

Required test groups:

- config precedence
  - global only
  - workspace override
  - workspace `hooks: []`
- pre-hook pipeline
  - mutation
  - block
  - confirmation request
  - multi-hook order
- confirmation resume
  - approve
  - deny
  - approve with edited fields
- post-hook replacement
  - keep
  - replace
  - chained replacement

## 17. Logging and Observability

First version should emit structured logs for:

- matched hooks per tool event
- pre-hook argument mutation
- confirmation requested by hook
- confirmation approved or denied
- post-hook result replacement

This is sufficient for the first version without introducing a management UI.

## 18. Implementation Notes

To avoid losing already-completed tool results when one tool call in a batch suspends for confirmation, tool execution should be coordinated per tool call rather than only as a single all-or-nothing batch.

The implementation may introduce a dedicated coordinator such as `ToolExecutionCoordinator` if required to:

- append completed tool responses incrementally
- suspend mid-batch safely
- resume from the suspended call rather than restarting the batch

## 19. First-Version Scope

Included:

- agent-scoped pre and post tool hooks
- global plus workspace config source support
- explicit `hooks: []` disable semantics
- dedicated `TOOL_CONFIRMATION` SSE event
- hook-driven confirmation card content with Chinese labels
- form-based parameter editing
- post-hook result replacement

Deferred:

- script hooks
- LLM lifecycle hooks
- generic JSON editors
- hook admin tooling
- non-tool lifecycle hook families
