# Chat Streaming Refactor Design

Date: 2026-05-23
Status: Draft for review

## 1. Background

`apex-agent` already supports:

- HTTP entrypoints for chat SSE in `ChatController`
- session-level mutual exclusion through `SessionExecutionGuard`
- session create/resume logic in `SuperAgentFactory`
- agent execution in `SuperAgent`
- runtime persistence and status finalization through `ExecutionFinalizer`
- frontend SSE consumption through `apex-frontend/src/stores/session/reducer.ts`

The current chat streaming path is functionally complete but structurally weak.

Current `ChatController` responsibilities include:

- request validation
- session lock acquisition and release
- request type branching for new vs resume
- user context propagation
- fixed thread-pool ownership
- SSE terminal event assembly
- exception logging and stream completion

This coupling has caused a correctness issue, not just a style issue.

`SuperAgent.execute()` already marks runtime failures as `ExecutionStatus.FAILED` before rethrowing. However, `ChatController` catches `Exception`, only logs it, and still sends `EndMessage` plus `emitter.complete()`. The frontend currently treats `END` as a successful terminal state unless it is already waiting for human input or tool confirmation. As a result, server-side execution failure is masked as normal completion at the protocol level.

## 2. Problem Summary

The current design has four concrete problems.

### 2.1 Controller boundary is too wide

`ChatController` mixes transport concerns, asynchronous orchestration, session lifecycle handling, and SSE protocol assembly in one class.

### 2.2 Failure semantics are incorrect

Runtime exceptions are swallowed at the controller boundary. The stream still terminates with a normal `END` event, so the client cannot distinguish:

- completed execution
- suspended execution waiting for human input
- failed execution

### 2.3 Async resource ownership is unclear

The controller constructs its own fixed thread pool. That pool is not Spring-managed, has no central configuration, and owns request execution behavior despite being part of application infrastructure.

### 2.4 Core responsibilities are blurred

`SuperAgentFactory` is not only creating and restoring session context. It also resolves runtime dependencies and invokes `SuperAgent`. Its actual role is wider than its name suggests.

## 3. Goals

- Make `ChatController` a thin HTTP adapter.
- Make stream termination explicit for `COMPLETED`, `FAILED`, and `HUMAN_IN_THE_LOOP`.
- Move async orchestration and cleanup into a dedicated application service.
- Move thread-pool lifecycle into Spring configuration.
- Split session lifecycle responsibilities from execution responsibilities in the core layer.
- Keep frontend protocol migration incremental and backward-compatible where practical.

## 4. Non-Goals

- changing the `END` event name in this version
- redesigning the entire SSE message taxonomy
- adding cancellation or retry semantics
- changing the internal execution loop in `SuperAgentLoopRunner`
- changing persistence format beyond what is needed for explicit terminal status
- broad refactoring outside chat streaming and closely related session orchestration

## 5. Current Behavior Analysis

### 5.1 Current backend flow

`ChatController`:

1. validates `sessionId`
2. acquires `SessionExecutionGuard`
3. reads `UserContextHolder`
4. creates `SseEmitter`
5. submits a task into a controller-owned executor
6. calls either `SuperAgentFactory.createContext()` or `SuperAgentFactory.resumeContext()`
7. sets the emitter on the context
8. calls `SuperAgentFactory.executeContext()`
9. catches all exceptions and logs them
10. always sends `EndMessage` if context exists
11. always completes the emitter
12. releases the guard and clears user context

### 5.2 Current core flow

`SuperAgent.execute()`:

1. resumes human-in-the-loop state if applicable
2. runs the execution loop
3. converts runtime failure from `IN_PROGRESS` to `FAILED`
4. rethrows the failure
5. always finalizes persistence in `ExecutionFinalizer`

This means the core layer already knows whether execution failed. The transport layer is where that information is currently lost.

### 5.3 Current frontend flow

The reducer currently maps `END` to:

- `waiting-confirmation` if pending confirmations exist
- `waiting-human` if pending prompts still exist
- otherwise `completed`

There is no protocol field for explicit execution failure. Therefore a failed backend run is rendered as completed if the stream does not end in a waiting state.

## 6. Chosen Approach

Use a dedicated chat streaming application service as the orchestration boundary, and split `SuperAgentFactory` into a session-focused service plus an execution-focused service.

Reasoning:

- `ChatController` should not own asynchronous execution infrastructure.
- session lifecycle and execution lifecycle are separate concerns and should be modeled separately.
- the system already has a stable core runtime (`SuperAgent`) and stable client event type (`END`), so the lowest-risk change is to preserve the event name and enrich its terminal context.
- incremental compatibility matters because the frontend already consumes `END`.

## 7. Target Architecture

### 7.1 `ChatController`

Responsibility:

- validate HTTP input
- acquire request-level guard
- create and return `SseEmitter`
- delegate orchestration to an application service

It should not:

- create or own a thread pool
- set or clear user context inside worker logic
- assemble terminal SSE payloads
- call the execution engine directly

### 7.2 `ChatStreamingApplicationService`

Responsibility:

- orchestrate one streaming request end-to-end
- choose create vs resume path
- submit the work to the configured executor
- bind the emitter to the session context
- translate runtime termination into explicit terminal SSE semantics
- release the already-acquired session guard and cleanup context reliably

This is the primary new orchestration boundary.

### 7.3 `ChatTerminalEventFactory`

Responsibility:

- build terminal `EndMessage` payloads from `SuperAgentContext`
- build terminal `EndMessage` payloads for failures that occur before a `SuperAgentContext` exists
- populate shared context keys such as:
  - `mode`
  - `stage_id`
  - `execution_status`
  - optional `error_code`
  - optional `error_message`

This removes protocol assembly from the controller and keeps terminal semantics centralized.

### 7.4 `ChatExecutionConfiguration`

Responsibility:

- define a named Spring-managed executor for chat streams
- define a `TaskDecorator` for `UserContextHolder` propagation and cleanup

This moves async infrastructure into application configuration where it belongs.

### 7.5 `SuperAgentSessionService`

Responsibility:

- create new turn context
- validate existing session ownership and compatibility
- restore resumable context
- prepare runtime tools and execution mode
- persist turn initialization state

This service owns session lifecycle and runtime preparation.

Resume contract:

- `resumeContext(sessionId, agentKey, humanResponse)`
- validate suspended-session ownership against the current caller
- validate suspended-session agent compatibility against the requested `agentKey`
- reject mismatched resume requests explicitly instead of mutating restored context identity

### 7.6 `SuperAgentExecutor`

Responsibility:

- invoke `SuperAgent.execute(context)`

This service is intentionally small. It separates "prepare context" from "run context".

### 7.7 `SuperAgentFactory`

Role in this version:

- compatibility facade only

It may remain temporarily to limit blast radius, but it should delegate to `SuperAgentSessionService` and `SuperAgentExecutor`. New orchestration code should depend on the split services directly.

## 8. Terminal Event Contract

### 8.1 Keep `END` as the terminal event

The event name remains `END` in this version.

Reasoning:

- existing frontend code already understands `END`
- changing the event name would force a coordinated contract migration across more code paths
- the correctness issue is not the event name; it is the missing terminal status

### 8.2 Add explicit terminal context

`EndMessage.context` should include:

- `mode`
- `stage_id` when relevant
- `execution_status`
- `error_code` when failed
- `error_message` when failed

Suggested values for `execution_status`:

- `COMPLETED`
- `FAILED`
- `HUMAN_IN_THE_LOOP`

### 8.3 Failure handling rule

When runtime execution fails after the stream has been accepted, the server must best-effort emit an explicit failed terminal event.

There are two sub-cases:

- failure with a valid `SuperAgentContext`
- failure before a `SuperAgentContext` exists

For failure with a valid `SuperAgentContext`:

- preserve contextual fields already derived from the session, such as `mode` and `stage_id`
- do not reuse a stale in-memory execution status for the terminal payload
- best-effort send `END` with `execution_status=FAILED`
- include stable machine-readable `error_code`
- include best-effort `error_message`
- if terminal event sending fails, log the send failure explicitly and still close the emitter

For failure before a valid `SuperAgentContext` exists:

- best-effort send `END` with `execution_status=FAILED`
- include stable machine-readable `error_code`
- include best-effort `error_message`
- do not silently `complete()` the emitter without a terminal failure event
- if terminal event sending fails, log the send failure explicitly and still close the emitter

### 8.4 Resume-not-allowed rule

If a `HUMAN_RESPONSE` request cannot be resumed because no valid suspended session exists:

- do not silently `complete()` the emitter
- treat this as an explicit failure path
- emit `END` with `execution_status=FAILED` if the stream has already been accepted

If a `HUMAN_RESPONSE` request targets a suspended session that belongs to another user or another agent:

- treat this as an explicit failure path rather than mutating the restored context to the caller's identity
- keep ownership and agent compatibility validation inside the session service
- validate agent compatibility against the caller-provided `agentKey`, not only against restored session state
- emit `END` with `execution_status=FAILED` if the stream has already been accepted

In this design, once the application has created the `SseEmitter` and delegated execution into the stream orchestration path, all subsequent failures are expressed as best-effort `END(FAILED)` emission even if session creation or resume fails before `SuperAgentContext` exists.

### 8.5 Server-side failure visibility rule

Failed terminal SSE events solve client-side visibility only.

The server must also keep explicit operational visibility:

- log handled stream failures at error level once
- attach machine-readable `error_code` for metric tagging or future monitoring
- avoid silently swallowing exceptions
- log terminal-event send failure explicitly when `END(FAILED)` cannot be written to the emitter

After a failure has been logged and a failed terminal event has been emitted, the worker should not rethrow the same exception into the executor's uncaught-exception path unless a separate global error hook is intentionally being used. Double-reporting the same failure is less useful than one explicit client failure plus one explicit server failure.

## 9. Async Execution Model

### 9.1 Executor ownership

The chat stream executor should be defined as a Spring bean.

Recommended initial shape:

- fixed core size matching current behavior
- named thread prefix for observability
- bounded queue

### 9.2 User context propagation

`UserContextHolder` propagation should move into executor infrastructure via `TaskDecorator`.

The propagation source should be singular:

- capture user context from the caller thread's `UserContextHolder`
- do not keep a parallel `userId` method parameter as a second source of truth
- executor-managed tasks should read user identity from the propagated holder only

Reasoning:

- avoids manual `set/clear` duplication in business code
- prevents future cleanup omissions
- makes async execution semantics consistent across all chat stream tasks

### 9.3 Cleanup guarantees

The orchestration service must guarantee the following in a final cleanup block:

- `SessionExecutionGuard.release(sessionId)`
- `UserContextHolder.clear()` when still needed outside the decorator boundary
- emitter completion

Cleanup must happen regardless of success, suspension, or failure.

### 9.4 Terminal send path

Terminal SSE emission should not go through a helper that swallows `IOException`.

Requirements:

- the orchestration service owns terminal event writing directly
- terminal send failure is logged explicitly with session context when available
- task-submission rejection and runtime-failure paths use the same terminal-send logic

## 10. Core Service Split

### 10.1 Why split `SuperAgentFactory`

The current class performs three different categories of work:

- session retrieval and validation
- runtime context assembly
- execution dispatch

These responsibilities change for different reasons and should not be coupled.

### 10.2 Split strategy

Move these methods into `SuperAgentSessionService`:

- `createContext`
- `resumeContext`
- `validateExistingSessionForNewTurn`
- `initializeNewTurn`
- `persistNewTurn`
- `prepareRuntimeContext`

Move execution dispatch into `SuperAgentExecutor`.

`resumeContext(sessionId, agentKey, humanResponse)` should not use `null` as a failure signal in the final design.

Recommended contract:

- return a valid `SuperAgentContext` on success
- throw an explicit domain exception, such as `SessionResumeNotAllowedException`, when resume is not allowed
- reject resume when session ownership or agent compatibility does not match the caller

This keeps resume failure explicit at the service boundary instead of relying on orchestration code to reinterpret `null`.

Leave `SuperAgentFactory` as a delegating facade until all callers are migrated.

## 11. Error Model

This refactor introduces two error categories.

### 11.1 Pre-stream request errors

Examples:

- blank `sessionId`
- malformed request body
- concurrent execution conflict

These remain synchronous request failures and should continue to surface as HTTP 4xx or similar application errors.

### 11.2 In-stream execution errors

Examples:

- executor submission failure after stream acceptance
- session ownership validation failure during create/resume
- session compatibility validation failure during create/resume
- resume-not-allowed failure during create/resume
- resume ownership or agent compatibility validation failure during create/resume
- failure while creating or restoring context inside async flow
- model streaming failure
- execution engine runtime exception

These should become best-effort explicit terminal SSE failures rather than silent normal completion.

### 11.3 Boundary rule

The error boundary is fixed as follows:

- errors detected before `SseEmitter` creation remain synchronous HTTP failures
- errors detected after `SseEmitter` creation are handled by best-effort `END(FAILED)` emission

This means ownership and compatibility checks may remain inside session creation and resume logic without creating an ambiguous contract. If those checks run after the emitter has been created, they are treated as in-stream failures and must still surface as explicit `FAILED` terminal events.

## 12. Frontend Impact

### 12.1 Type changes

`EnvelopeContext` should be extended with:

- `execution_status`
- `error_code`
- `error_message`

### 12.2 Reducer changes

`END` handling should become:

- `FAILED` -> `error`
- `HUMAN_IN_THE_LOOP` with pending confirmation -> `waiting-confirmation`
- `HUMAN_IN_THE_LOOP` with pending prompts -> `waiting-human`
- `COMPLETED` -> `completed`
- missing `execution_status` -> preserve the current fallback behavior based on pending confirmations and prompts

### 12.3 Compatibility note

Older `END` payloads without `execution_status` should continue to use the existing pending-confirmation and pending-prompt fallback behavior during migration, while new backend behavior should always populate the field.

## 13. Migration Plan

The implementation should proceed in this order.

### Phase 1: Terminal contract correction

- enrich `END` context
- update backend terminal event emission so `execution_status` is actually sent on success, suspension, and failure
- cover failures after `SseEmitter` acceptance even when `SuperAgentContext` was not created successfully yet
- update frontend reducer semantics
- add focused tests for `FAILED` and `HUMAN_IN_THE_LOOP`

This fixes terminal failure visibility first.

It does not yet complete resume ownership and agent-compatibility hardening, because the old resume contract still lacks caller-provided `agentKey`.

### Phase 2: Stream lifecycle extraction

- add `ChatStreamingApplicationService`
- add `ChatTerminalEventFactory`
- slim `ChatController`
- keep task-submission rejection handling in this phase so controller-side lock acquisition cannot leak
- keep this first extraction phase dependent on `SuperAgentFactory` to avoid coupling the orchestration refactor to the core split

This fixes controller boundary issues without changing the already-corrected terminal contract.

### Phase 3: Spring-managed async execution

- add executor configuration
- move context propagation into `TaskDecorator`

This fixes async lifecycle ownership.

### Phase 4: Core split

- introduce `SuperAgentSessionService`
- introduce `SuperAgentExecutor`
- reduce `SuperAgentFactory` to facade
- switch `ChatStreamingApplicationService` from `SuperAgentFactory` to the split services

This fixes core responsibility boundaries and completes explicit resume ownership and agent-compatibility validation.

## 14. Testing Strategy

### 14.1 Backend tests

Add or update tests for:

- controller request validation and delegation
- stream lifecycle success path
- stream lifecycle failure path
- guard release on failure
- resume-not-allowed behavior
- session service create/resume validation
- terminal event payload generation

### 14.2 Frontend tests

Add or update tests for:

- `END` with `COMPLETED`
- `END` with `FAILED`
- `END` with `HUMAN_IN_THE_LOOP`
- store-level stream transitions using explicit terminal status

## 15. Risks and Tradeoffs

### 15.1 Keeping `END` avoids a larger migration

Tradeoff:

- terminal semantics are still partly encoded in `context` rather than event type

Decision:

- acceptable for this version because it minimizes migration cost while solving the real bug

### 15.2 Temporary facade adds short-term duplication

Tradeoff:

- `SuperAgentFactory` may exist briefly alongside the new services

Decision:

- acceptable to reduce rollout risk and keep call sites stable during transition

### 15.3 Error message exposure must stay bounded

Tradeoff:

- rich `error_message` improves debugging but can expose unstable internal strings

Decision:

- include machine-readable `error_code`
- keep `error_message` best-effort and avoid relying on exact message text in frontend logic

## 16. Decision Summary

The approved direction for planning is:

- thin `ChatController`
- dedicated `ChatStreamingApplicationService`
- dedicated terminal event factory
- Spring-managed executor with context propagation
- explicit terminal `execution_status`
- explicit `END(FAILED)` for failures even when no `SuperAgentContext` exists yet
- split session lifecycle from execution lifecycle in the core layer

This is the smallest coherent refactor that fixes the current correctness bug and establishes cleaner boundaries for future work.
