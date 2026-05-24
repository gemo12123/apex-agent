# Chat Streaming Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor chat SSE execution so controller responsibilities stay thin, async execution is Spring-managed, and stream termination explicitly distinguishes completed, failed, and human-waiting outcomes.

**Architecture:** First correct the terminal SSE contract in-place so the backend actually emits explicit `execution_status` values for all post-accept terminal outcomes, including failures that happen before `SuperAgentContext` is created. Then move SSE orchestration out of `ChatController` into a dedicated application service that owns async dispatch, terminal event emission, task-submission failure handling, and post-acquire cleanup, while `ChatController` remains responsible for request validation, `SseEmitter` creation, and session-lock acquisition. Keep the first extraction phase dependent on `SuperAgentFactory` so the lifecycle refactor can land before the core split, then introduce `SuperAgentSessionService` and `SuperAgentExecutor` in a later phase with explicit resume ownership and agent compatibility checks based on the caller-provided `agentKey`.

**Phase boundary note:** Phase 1 fixes terminal failure visibility for post-accept failures. Explicit resume ownership and agent-compatibility validation across `HUMAN_RESPONSE` remains a Phase 4 contract fix because the legacy resume path still does not accept caller-provided `agentKey`.

**Tech Stack:** Java 25, Spring Boot 3.4, Spring MVC `SseEmitter`, Spring `ThreadPoolTaskExecutor`, JUnit 5, Mockito, Vue 3, Pinia, Vitest, TypeScript

---

## File Map

**Backend stream contract**

- Modify: `apex-agent/src/main/java/org/gemo/apex/constant/ContextKeyEnum.java`
  - Add explicit terminal status and error context keys for SSE completion events.
- Modify: `apex-agent/src/main/java/org/gemo/apex/message/EndMessage.java`
  - Keep the event shape stable while documenting the richer terminal context contract.
- Modify: `apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java`
  - As a Phase 1 correctness patch, emit explicit terminal status from the current controller flow, including failures before a session context exists.
- Modify: `apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java`
  - Cover success, suspended, and failed terminal event emission in the temporary controller-owned flow.

**Backend orchestration**

- Modify: `apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java`
  - Convert the temporary correctness patch into constructor injection plus delegation to a dedicated application service.
- Create: `apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java`
  - Own async dispatch, context lookup, terminal event emission, and cleanup after controller-side lock acquisition.
- Create: `apex-agent/src/main/java/org/gemo/apex/web/service/ChatTerminalEventFactory.java`
  - Build terminal `EndMessage` payloads for completed, failed, and suspended executions, including failed terminations when no `SuperAgentContext` exists yet.
- Create: `apex-agent/src/main/java/org/gemo/apex/config/ChatExecutionConfiguration.java`
  - Expose a named `ThreadPoolTaskExecutor` and `TaskDecorator` for `UserContextHolder` propagation.

**Backend core split**

- Create: `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentSessionService.java`
  - Absorb session create/resume/validation/runtime-preparation logic from `SuperAgentFactory`.
- Create: `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentExecutor.java`
  - Wrap `SuperAgent.execute(context)` behind a focused interface.
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java`
  - Convert to a compatibility facade that delegates to `SuperAgentSessionService` and `SuperAgentExecutor`.

**Backend tests**

- Modify: `apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java`
  - Re-scope tests to request validation plus delegation into `ChatStreamingApplicationService`.
- Create: `apex-agent/src/test/java/org/gemo/apex/web/service/ChatStreamingApplicationServiceTest.java`
  - Verify completion, failure with context, failure without context, guard release behavior, best-effort terminal emission, logging, and user context cleanup.
- Create: `apex-agent/src/test/java/org/gemo/apex/web/service/ChatTerminalEventFactoryTest.java`
  - Verify terminal `EndMessage` context assembly.
- Modify: `apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java`
  - Convert current behavior checks to `SuperAgentSessionServiceTest` and trim facade coverage.
- Create: `apex-agent/src/test/java/org/gemo/apex/core/SuperAgentSessionServiceTest.java`
  - Cover new-session, next-turn, resume, ownership validation, and agent compatibility validation paths.

**Frontend stream contract**

- Modify: `apex-frontend/src/types/apex.ts`
  - Extend `EnvelopeContext` with `execution_status`, `error_code`, and `error_message`.
- Modify: `apex-frontend/src/stores/session/reducer.ts`
  - Interpret terminal context correctly, while preserving legacy fallback behavior when `execution_status` is absent.
- Modify: `apex-frontend/src/stores/session/reducer.test.ts`
  - Add failed/completed/human-waiting `END` cases.
- Modify: `apex-frontend/src/stores/session/store.test.ts`
  - Update stream fixtures that assert terminal state.

## Task 1: Enrich the Terminal SSE Contract

**Files:**
- Modify: `apex-agent/src/main/java/org/gemo/apex/constant/ContextKeyEnum.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/message/EndMessage.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java`
- Modify: `apex-frontend/src/types/apex.ts`
- Test: `apex-frontend/src/stores/session/reducer.test.ts`
- Modify: `apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java`

- [ ] **Step 1: Write the failing frontend reducer tests for terminal status handling**

```ts
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

it('keeps waiting-human when END carries HUMAN_IN_THE_LOOP execution status', () => {
  let state = createSessionViewModel()
  state = applyEnvelope(state, {
    event_type: 'TOOL_CONFIRMATION',
    context: { mode: 'react' },
    messages: [{
      confirmation_id: 'confirm-1',
      tool_call_id: 'tool-call-1',
      invocation_id: 'invoke-1',
      tool_name: 'deploy_tool',
      tool_display_name: 'Deploy Tool',
      title: 'Continue?',
      risk_level: 'MEDIUM',
      editable: false,
      confirm_label: 'Approve',
      deny_label: 'Deny',
      display_fields: [],
      editable_fields: [],
    }],
  } satisfies SseEnvelope)

  state = applyEnvelope(state, {
    event_type: 'END',
    context: { mode: 'react', execution_status: 'HUMAN_IN_THE_LOOP' },
    messages: [],
  } satisfies SseEnvelope)

  expect(state.status).toBe('waiting-confirmation')
})

it('preserves legacy waiting behavior when END has no execution_status', () => {
  let state = createSessionViewModel()
  state = applyEnvelope(state, {
    event_type: 'ASK_HUMAN',
    context: { mode: 'react' },
    messages: [{ input_type: 'TEXT', question: 'Need more input', tool_call_id: 'ask-1' }],
  } satisfies SseEnvelope)

  state = applyEnvelope(state, {
    event_type: 'END',
    context: { mode: 'react' },
    messages: [],
  } satisfies SseEnvelope)

  expect(state.status).toBe('waiting-human')
})
```

- [ ] **Step 2: Run the frontend reducer test file to verify it fails**

Run:

```bash
pwsh -NoLogo -Command "cmd /c npm run test:run -- src/stores/session/reducer.test.ts"
```

Expected: FAIL because `EnvelopeContext` and `applyEnvelope()` do not know `execution_status` yet.

- [ ] **Step 3: Add the terminal context fields on the frontend and backend constants**

```java
// apex-agent/src/main/java/org/gemo/apex/constant/ContextKeyEnum.java
public enum ContextKeyEnum {
    MODE("mode"),
    STAGE_ID("stage_id"),
    TASK_ID("task_id"),
    INVOCATION_ID("invocation_id"),
    ARTIFACT_ID("artifact_id"),
    EXECUTOR("executor"),
    CONTENT_ID("content_id"),
    EXECUTION_STATUS("execution_status"),
    ERROR_CODE("error_code"),
    ERROR_MESSAGE("error_message");
}
```

```ts
// apex-frontend/src/types/apex.ts
export interface EnvelopeContext {
  mode?: 'react' | 'plan-executor' | string
  stage_id?: string
  executor?: string
  content_id?: string
  invocation_id?: string
  execution_status?: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'HUMAN_IN_THE_LOOP' | string
  error_code?: string
  error_message?: string
}
```

- [ ] **Step 4: Update reducer END handling to respect terminal execution status**

```ts
// apex-frontend/src/stores/session/reducer.ts
case 'END': {
  const executionStatus = context.execution_status

  if (!executionStatus) {
    nextState.status = nextState.pendingConfirmations.length > 0
      ? 'waiting-confirmation'
      : nextState.pendingPrompts.some((prompt) => !prompt.answered)
        ? 'waiting-human'
        : 'completed'
    return nextState
  }

  if (executionStatus === 'FAILED') {
    nextState.status = 'error'
    return nextState
  }

  if (executionStatus === 'HUMAN_IN_THE_LOOP') {
    nextState.status = nextState.pendingConfirmations.length > 0
      ? 'waiting-confirmation'
      : 'waiting-human'
    return nextState
  }

  nextState.status = 'completed'
  return nextState
}
```

- [ ] **Step 5: Write the failing backend controller tests for explicit terminal status emission**

```java
@Test
void executeWithSseShouldEmitFailedEndWhenExecutionThrows() {
    ChatRequest request = new ChatRequest();
    request.setSessionId("session-1");
    request.setAgentKey("default_agent");
    request.setQuery("hello");

    SuperAgentContext context = new SuperAgentContext();
    context.setSessionId("session-1");
    context.setExecutionMode(ModeEnum.REACT);
    context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
    when(superAgentFactory.createContext("session-1", "default_agent", "hello")).thenReturn(context);
    doThrow(new IllegalStateException("boom")).when(superAgentFactory).executeContext(context);

    controller.executeWithSse(request);

    try (MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
        controller.executeWithSse(request);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            mockedMessageUtils.verify(() -> MessageUtils.sendMessage(
                    same(context),
                    argThat(message -> message instanceof EndMessage endMessage
                            && "FAILED".equals(endMessage.getContext().get("execution_status"))
                            && "STREAM_EXECUTION_FAILED".equals(endMessage.getContext().get("error_code")))));
        });
    }
}

@Test
void executeWithSseShouldEmitFailedEndWhenCreateContextThrowsBeforeContextExists() {
    when(superAgentFactory.createContext("session-1", "default_agent", "hello"))
            .thenThrow(new IllegalStateException("Session session-1 belongs to another user"));

    String jsonRequest = """
            {
              "sessionId":"session-1",
              "agentKey":"default_agent",
              "query":"hello",
              "type":"NEW"
            }
            """;

    MvcResult mvcResult = mockMvc.perform(post("/api/sse/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonRequest))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"execution_status\":\"FAILED\"")))
            .andExpect(content().string(containsString("\"error_code\":\"STREAM_CONTEXT_INIT_FAILED\"")));
}

@Test
void executeWithSseShouldEmitFailedEndWhenResumeContextIsNotResumable() {
    when(superAgentFactory.resumeContext("session-1", Map.of("tool-call-1", "approved"))).thenReturn(null);

    String jsonRequest = """
            {
              "sessionId":"session-1",
              "agentKey":"default_agent",
              "type":"HUMAN_RESPONSE",
              "humanResponse":{"tool-call-1":"approved"}
            }
            """;

    MvcResult mvcResult = mockMvc.perform(post("/api/sse/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonRequest))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"execution_status\":\"FAILED\"")))
            .andExpect(content().string(containsString("\"error_code\":\"STREAM_CONTEXT_INIT_FAILED\"")));
}

@Test
void executeWithSseShouldEmitCompletedEndWhenExecutionFinishesNormally() {
    ChatRequest request = new ChatRequest();
    request.setSessionId("session-1");
    request.setAgentKey("default_agent");
    request.setQuery("hello");

    SuperAgentContext context = new SuperAgentContext();
    context.setSessionId("session-1");
    context.setExecutionMode(ModeEnum.REACT);
    context.setExecutionStatus(ExecutionStatus.COMPLETED);
    when(superAgentFactory.createContext("session-1", "default_agent", "hello")).thenReturn(context);

    try (MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
        controller.executeWithSse(request);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            mockedMessageUtils.verify(() -> MessageUtils.sendMessage(
                    same(context),
                    argThat(message -> message instanceof EndMessage endMessage
                            && "COMPLETED".equals(endMessage.getContext().get("execution_status")))));
        });
    }
}
```

- [ ] **Step 6: Run the frontend and controller tests to verify they fail**

Run:

```bash
pwsh -NoLogo -Command "cmd /c npm run test:run -- src/stores/session/reducer.test.ts"
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatControllerTest test"
```

Expected: FAIL because the backend still does not emit `execution_status`, `error_code`, or `error_message` in its `END` payload, and resume/init failures after stream acceptance are still completed silently. The temporary controller-owned tests should use static mocking of `MessageUtils.sendMessage(...)` when a `SuperAgentContext` exists, and assert raw SSE response content for failures that happen before context creation.

- [ ] **Step 7: Patch the current controller flow to emit explicit terminal status before lifecycle extraction**

```java
// apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java
private Map<String, Object> buildEndContext(SuperAgentContext context, String errorCode, String errorMessage) {
    Map<String, Object> map = new HashMap<>();
    map.put(ContextKeyEnum.MODE.getKey(),
            context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
    if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getCurrentStageId() != null) {
        map.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
    }

    if (StringUtils.hasText(errorCode)) {
        map.put(ContextKeyEnum.EXECUTION_STATUS.getKey(), ExecutionStatus.FAILED.name());
        map.put(ContextKeyEnum.ERROR_CODE.getKey(), errorCode);
        map.put(ContextKeyEnum.ERROR_MESSAGE.getKey(), errorMessage);
    } else {
        map.put(ContextKeyEnum.EXECUTION_STATUS.getKey(),
                context.getExecutionStatus() != null ? context.getExecutionStatus().name() : ExecutionStatus.COMPLETED.name());
    }
    return Map.copyOf(map);
}
```

Implementation note:

- when `createContext()` throws after the emitter has been created, send `END(FAILED)` with `STREAM_CONTEXT_INIT_FAILED`
- when `resumeContext()` returns `null` in the transitional controller-owned flow, translate it into the same explicit failure event instead of silently completing the stream

- [ ] **Step 8: Re-run the frontend and controller tests**

Run:

```bash
pwsh -NoLogo -Command "cmd /c npm run test:run -- src/stores/session/reducer.test.ts"
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatControllerTest test"
```

Expected: PASS with both explicit terminal statuses and legacy fallback behavior covered, and with the current controller flow no longer masking failures as normal completion.

- [ ] **Step 9: Commit the terminal contract change**

```bash
git add apex-agent/src/main/java/org/gemo/apex/constant/ContextKeyEnum.java apex-agent/src/main/java/org/gemo/apex/message/EndMessage.java apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java apex-frontend/src/types/apex.ts apex-frontend/src/stores/session/reducer.ts apex-frontend/src/stores/session/reducer.test.ts
git commit -m "feat: add explicit terminal execution status"
```

## Task 2: Extract Terminal Event Assembly and Stream Lifecycle Orchestration

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/web/service/ChatTerminalEventFactory.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/web/service/ChatTerminalEventFactoryTest.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/web/service/ChatStreamingApplicationServiceTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java`

- [ ] **Step 1: Write the failing terminal factory tests**

```java
@Test
void buildFailureEndMessageShouldExposeExecutionStatusAndErrorMetadata() {
    SuperAgentContext context = new SuperAgentContext();
    context.setExecutionMode(ModeEnum.REACT);
    context.setExecutionStatus(ExecutionStatus.FAILED);

    EndMessage endMessage = factory.buildForFailure(context, "STREAM_EXECUTION_FAILED",
            "Model returned neither content nor tool calls");

    assertEquals("react", endMessage.getContext().get("mode"));
    assertEquals("FAILED", endMessage.getContext().get("execution_status"));
    assertEquals("STREAM_EXECUTION_FAILED", endMessage.getContext().get("error_code"));
}

@Test
void buildRequestFailureEndMessageShouldNotRequireSessionContext() {
    EndMessage endMessage = factory.buildForRequestFailure("STREAM_CONTEXT_INIT_FAILED",
            "Session session-1 is not resumable");

    assertEquals("FAILED", endMessage.getContext().get("execution_status"));
    assertEquals("STREAM_CONTEXT_INIT_FAILED", endMessage.getContext().get("error_code"));
    assertEquals("Session session-1 is not resumable", endMessage.getContext().get("error_message"));
}
```

- [ ] **Step 2: Write the failing application service tests for success and failure cleanup**

```java
@Test
void streamShouldReleaseSessionGuardAndSendFailedEndMessageWhenExecutorThrows() throws Exception {
    ChatRequest request = new ChatRequest();
    request.setSessionId("session-1");
    request.setAgentKey("default_agent");
    request.setQuery("hello");

    SseEmitter emitter = new SseEmitter(1000L);
    SuperAgentContext context = new SuperAgentContext();
    context.setSessionId("session-1");
    context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

    when(superAgentFactory.createContext("session-1", "default_agent", "hello")).thenReturn(context);
    doThrow(new IllegalStateException("boom")).when(superAgentFactory).executeContext(context);

    UserContextHolder.setUserId("user-1");
    try {
        service.stream(request, emitter);
    } finally {
        UserContextHolder.clear();
    }

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
    });
}

@Test
void streamShouldSendFailedEndMessageWhenCreateContextThrowsBeforeContextExists() {
    ChatRequest request = new ChatRequest();
    request.setSessionId("session-1");
    request.setAgentKey("default_agent");
    request.setQuery("hello");

    SseEmitter emitter = new SseEmitter(1000L);
    doThrow(new IllegalStateException("Session session-1 belongs to another user"))
            .when(superAgentFactory).createContext("session-1", "default_agent", "hello");

    UserContextHolder.setUserId("user-1");
    try {
        service.stream(request, emitter);
    } finally {
        UserContextHolder.clear();
    }

    verify(terminalEventFactory).buildForRequestFailure("STREAM_CONTEXT_INIT_FAILED",
            "Session session-1 belongs to another user");
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
    });
}

@Test
void streamShouldReleaseGuardWhenTaskSubmissionIsRejected() {
    ChatRequest request = new ChatRequest();
    request.setSessionId("session-1");
    request.setAgentKey("default_agent");
    request.setQuery("hello");

    SseEmitter emitter = new SseEmitter(1000L);
    doThrow(new TaskRejectedException("queue full"))
            .when(chatStreamExecutor).execute(any(Runnable.class));

    service.stream(request, emitter);

    verify(terminalEventFactory).buildForRequestFailure("STREAM_TASK_SUBMISSION_FAILED", "queue full");
    assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
}

@Test
void streamShouldLogTerminalSendFailureAndStillReleaseGuard() {
    ChatRequest request = new ChatRequest();
    request.setSessionId("session-1");
    request.setAgentKey("default_agent");
    request.setQuery("hello");

    SseEmitter emitter = mock(SseEmitter.class);
    SuperAgentContext context = new SuperAgentContext();
    context.setSessionId("session-1");
    context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

    when(superAgentFactory.createContext("session-1", "default_agent", "hello")).thenReturn(context);
    doThrow(new IllegalStateException("boom")).when(superAgentFactory).executeContext(context);
    doThrow(new IOException("client disconnected")).when(emitter).send(anyString());

    UserContextHolder.setUserId("user-1");
    try {
        service.stream(request, emitter);
    } finally {
        UserContextHolder.clear();
    }

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
    });
    assertThat(logCapture.events())
            .anyMatch(event -> event.level() == ERROR
                    && event.message().contains("Failed to send terminal SSE event")
                    && event.throwable() instanceof IOException);
}
```

Testing note:

- Task 2 service tests should assert terminal emission by mocking `SseEmitter.send(...)` directly
- only the transitional Task 1 controller tests should rely on static mocking of `MessageUtils.sendMessage(...)`

- [ ] **Step 3: Run the backend web/service tests to verify they fail**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatControllerTest,ChatTerminalEventFactoryTest,ChatStreamingApplicationServiceTest test"
```

Expected: FAIL because the new service and factory classes do not exist yet, and task-submission failure handling has not been introduced at the new orchestration boundary.

- [ ] **Step 4: Implement `ChatTerminalEventFactory` with explicit success/failure/suspended builders**

```java
// apex-agent/src/main/java/org/gemo/apex/web/service/ChatTerminalEventFactory.java
@Component
public class ChatTerminalEventFactory {

    public EndMessage buildForCompletion(SuperAgentContext context) {
        return build(context,
                context.getExecutionStatus() != null ? context.getExecutionStatus() : ExecutionStatus.COMPLETED,
                null, null);
    }

    public EndMessage buildForFailure(SuperAgentContext context, String errorCode, String errorMessage) {
        return build(context, ExecutionStatus.FAILED, errorCode, errorMessage);
    }

    public EndMessage buildForRequestFailure(String errorCode, String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ContextKeyEnum.EXECUTION_STATUS.getKey(), ExecutionStatus.FAILED.name());
        payload.put(ContextKeyEnum.ERROR_CODE.getKey(), errorCode);
        payload.put(ContextKeyEnum.ERROR_MESSAGE.getKey(), errorMessage);
        return EndMessage.builder().context(Map.copyOf(payload)).build();
    }

    private EndMessage build(SuperAgentContext context, ExecutionStatus executionStatus,
            String errorCode, String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ContextKeyEnum.MODE.getKey(),
                context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
        payload.put(ContextKeyEnum.EXECUTION_STATUS.getKey(), executionStatus.name());
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getCurrentStageId() != null) {
            payload.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
        }
        if (StringUtils.hasText(errorCode)) {
            payload.put(ContextKeyEnum.ERROR_CODE.getKey(), errorCode);
        }
        if (StringUtils.hasText(errorMessage)) {
            payload.put(ContextKeyEnum.ERROR_MESSAGE.getKey(), errorMessage);
        }
        return EndMessage.builder().context(Map.copyOf(payload)).build();
    }
}
```

- [ ] **Step 5: Implement `ChatStreamingApplicationService` and slim `ChatController` down to delegation**

```java
// apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java
@RestController
@RequestMapping("/api/sse")
public class ChatController {

    private final ApexGlobalProperties apexGlobalProperties;
    private final SessionExecutionGuard sessionExecutionGuard;
    private final ChatStreamingApplicationService chatStreamingApplicationService;

    public ChatController(ApexGlobalProperties apexGlobalProperties,
            SessionExecutionGuard sessionExecutionGuard,
            ChatStreamingApplicationService chatStreamingApplicationService) {
        this.apexGlobalProperties = apexGlobalProperties;
        this.sessionExecutionGuard = sessionExecutionGuard;
        this.chatStreamingApplicationService = chatStreamingApplicationService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWithSse(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId must not be blank");
        }
        if (!sessionExecutionGuard.tryAcquire(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session " + sessionId + " already has an active execution");
        }

        SseEmitter emitter = new SseEmitter(600000L);
        chatStreamingApplicationService.stream(request, emitter);
        return emitter;
    }
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java
@Service
public class ChatStreamingApplicationService {

    private final SuperAgentFactory superAgentFactory;
    private final SessionExecutionGuard sessionExecutionGuard;
    private final ChatTerminalEventFactory terminalEventFactory;
    private final TaskExecutor chatStreamExecutor;
    private static final Logger log = LoggerFactory.getLogger(ChatStreamingApplicationService.class);

    public void stream(ChatRequest request, SseEmitter emitter) {
        String userId = UserContextHolder.getUserId();
        try {
            chatStreamExecutor.execute(() -> {
            SuperAgentContext context = null;
            try {
                UserContextHolder.setUserId(userId);
                context = request.getType() == RequestType.HUMAN_RESPONSE
                        ? superAgentFactory.resumeContext(request.getSessionId(), request.getHumanResponse())
                        : superAgentFactory.createContext(request.getSessionId(), request.getAgentKey(), request.getQuery());

                if (context == null) {
                    throw new IllegalStateException("Session " + request.getSessionId() + " is not resumable");
                }

                context.setSseEmitter(emitter);
                superAgentFactory.executeContext(context);
                sendTerminalEvent(emitter, terminalEventFactory.buildForCompletion(context));
                emitter.complete();
            } catch (RuntimeException ex) {
                log.error("Chat stream execution failed, sessionId={}", request.getSessionId(), ex);
                if (context != null) {
                    if (context.getExecutionStatus() == null || context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
                        context.setExecutionStatus(ExecutionStatus.FAILED);
                    }
                    sendTerminalEvent(emitter, terminalEventFactory.buildForFailure(context,
                            "STREAM_EXECUTION_FAILED", ex.getMessage()));
                } else {
                    sendTerminalEvent(emitter,
                            terminalEventFactory.buildForRequestFailure("STREAM_CONTEXT_INIT_FAILED", ex.getMessage()));
                }
                emitter.complete();
            } finally {
                sessionExecutionGuard.release(request.getSessionId());
                UserContextHolder.clear();
            }
            });
        } catch (TaskRejectedException ex) {
            log.error("Chat stream task submission failed, sessionId={}", request.getSessionId(), ex);
            sendTerminalEvent(emitter,
                    terminalEventFactory.buildForRequestFailure("STREAM_TASK_SUBMISSION_FAILED", ex.getMessage()));
            emitter.complete();
            sessionExecutionGuard.release(request.getSessionId());
        }
    }

    private void sendTerminalEvent(SseEmitter emitter, EndMessage endMessage) {
        try {
            emitter.send(JacksonUtils.toJson(endMessage));
        } catch (IOException sendFailure) {
            log.error("Failed to send terminal SSE event", sendFailure);
        }
    }
}
```

Note:

- Task 2 intentionally keeps manual `UserContextHolder` capture and worker-thread `set/clear` so the extracted lifecycle service can pass with the current executor model.
- Task 3 removes this transitional propagation once `TaskDecorator` is introduced.
- Task 4 replaces this implicit failure channel with an explicit resume exception once callers move to `SuperAgentSessionService`.
- Terminal failure builders in this task must force `execution_status=FAILED` rather than mirroring any stale in-memory status value from the context.
- Task 2 must also handle `TaskRejectedException` in `stream()` itself so controller-side lock acquisition cannot leak when async dispatch is rejected.

- [ ] **Step 6: Re-run the backend controller/service tests**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatControllerTest,ChatTerminalEventFactoryTest,ChatStreamingApplicationServiceTest test"
```

Expected: PASS with controller delegation, transitional manual user-context propagation, terminal-event emission, and send-failure logging coverage green.

Implementation note:

- treat terminal-event sending as best effort
- if writing `END` fails because the connection is already gone, log that send failure explicitly and still close the emitter and release the guard

- [ ] **Step 7: Commit the controller and lifecycle extraction**

```bash
git add apex-agent/src/main/java/org/gemo/apex/web/controller/ChatController.java apex-agent/src/main/java/org/gemo/apex/web/service/ChatTerminalEventFactory.java apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java apex-agent/src/test/java/org/gemo/apex/web/service/ChatTerminalEventFactoryTest.java apex-agent/src/test/java/org/gemo/apex/web/service/ChatStreamingApplicationServiceTest.java
git commit -m "refactor: extract chat stream lifecycle service"
```

## Task 3: Move Async Execution to Spring-Managed Infrastructure

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/config/ChatExecutionConfiguration.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/web/service/ChatStreamingApplicationServiceTest.java`

- [ ] **Step 1: Keep the Task 2 submission-rejection test green while preparing to remove manual propagation**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatStreamingApplicationServiceTest test"
```

Expected: PASS for the task-submission rejection case added in Task 2, while context propagation is still manually handled.

- [ ] **Step 2: Remove manual `UserContextHolder` propagation from business code before writing the next test**

```java
// apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java
public void stream(ChatRequest request, SseEmitter emitter) {
    try {
        chatStreamExecutor.execute(() -> runStream(request, emitter));
    } catch (TaskRejectedException ex) {
        log.error("Chat stream task submission failed, sessionId={}", request.getSessionId(), ex);
        sendTerminalEvent(emitter,
                terminalEventFactory.buildForRequestFailure("STREAM_TASK_SUBMISSION_FAILED", ex.getMessage()));
        emitter.complete();
        sessionExecutionGuard.release(request.getSessionId());
    }
}

private void runStream(ChatRequest request, SseEmitter emitter) {
    // create/resume and execute without calling UserContextHolder.setUserId()/clear()
}
```

- [ ] **Step 3: Write the failing test that proves async user context must come from executor infrastructure**

```java
@Test
void streamShouldPropagateUserContextIntoAsyncTaskViaTaskDecorator() {
    ChatRequest request = new ChatRequest();
    request.setSessionId("session-1");
    request.setAgentKey("default_agent");
    request.setQuery("hello");

    SuperAgentContext context = new SuperAgentContext();
    context.setSessionId("session-1");
    when(superAgentFactory.createContext("session-1", "default_agent", "hello")).thenReturn(context);

    doAnswer(invocation -> {
        assertEquals("user-1", UserContextHolder.getUserId());
        return null;
    }).when(superAgentFactory).executeContext(context);

    UserContextHolder.setUserId("user-1");
    try {
        service.stream(request, new SseEmitter(1000L));
    } finally {
        UserContextHolder.clear();
    }
}
```

- [ ] **Step 4: Run the async context-propagation test**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatStreamingApplicationServiceTest test"
```

Expected: FAIL because manual business-code propagation has been removed, the test now relies solely on caller-thread `UserContextHolder`, and the executor `TaskDecorator` is not introduced yet.

- [ ] **Step 5: Add a named `ThreadPoolTaskExecutor` with `TaskDecorator`**

```java
// apex-agent/src/main/java/org/gemo/apex/config/ChatExecutionConfiguration.java
@Configuration
public class ChatExecutionConfiguration {

    @Bean(name = "chatStreamExecutor")
    public ThreadPoolTaskExecutor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-stream-");
        executor.setTaskDecorator(runnable -> {
            String userId = UserContextHolder.getUserId();
            return () -> {
                try {
                    UserContextHolder.setUserId(userId);
                    runnable.run();
                } finally {
                    UserContextHolder.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 6: Keep business code free of manual propagation and rely on the executor decorator**

```java
// apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java
public ChatStreamingApplicationService(...,
        @Qualifier("chatStreamExecutor") TaskExecutor chatStreamExecutor) {
    this.chatStreamExecutor = chatStreamExecutor;
}

public void stream(ChatRequest request, SseEmitter emitter) {
    try {
        chatStreamExecutor.execute(() -> runStream(request, emitter));
    } catch (TaskRejectedException ex) {
        log.error("Chat stream task submission failed, sessionId={}", request.getSessionId(), ex);
        sendTerminalEvent(emitter,
                terminalEventFactory.buildForRequestFailure("STREAM_TASK_SUBMISSION_FAILED", ex.getMessage()));
        emitter.complete();
        sessionExecutionGuard.release(request.getSessionId());
    }
}

private void runStream(ChatRequest request, SseEmitter emitter) {
    // use UserContextHolder.getUserId() from the decorator-managed thread only
}
```

- [ ] **Step 7: Re-run the async orchestration tests**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatStreamingApplicationServiceTest test"
```

Expected: PASS with both submission rejection handling and decorator-based user-context propagation covered.

- [ ] **Step 8: Commit the Spring-managed executor change**

```bash
git add apex-agent/src/main/java/org/gemo/apex/config/ChatExecutionConfiguration.java apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java apex-agent/src/test/java/org/gemo/apex/web/service/ChatStreamingApplicationServiceTest.java
git commit -m "refactor: manage chat stream execution with spring executor"
```

## Task 4: Split Session Lifecycle from Execution in the Core Layer

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentSessionService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentExecutor.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/core/SuperAgentSessionServiceTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java`

- [ ] **Step 1: Copy the current `SuperAgentFactory` behavior into a new focused test class before moving code**

```java
// apex-agent/src/test/java/org/gemo/apex/core/SuperAgentSessionServiceTest.java
@Test
void createContextShouldRejectSessionOfAnotherAgent() {
    SuperAgentContext existingContext = new SuperAgentContext();
    existingContext.setSessionId("session-1");
    existingContext.setUserId("user-1");
    existingContext.setAgentKey("agent-2");
    existingContext.setExecutionStatus(ExecutionStatus.COMPLETED);
    when(sessionContextStore.load("session-1")).thenReturn(Optional.of(existingContext));

    assertThrows(IllegalStateException.class,
            () -> sessionService.createContext("session-1", "agent-1", "follow up"));
}

@Test
void resumeContextShouldRejectSessionOfAnotherUser() {
    SuperAgentContext existingContext = new SuperAgentContext();
    existingContext.setSessionId("session-1");
    existingContext.setUserId("user-2");
    existingContext.setAgentKey("agent-1");
    existingContext.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
    when(sessionContextStore.load("session-1")).thenReturn(Optional.of(existingContext));
    UserContextHolder.setUserId("user-1");

    try {
        assertThrows(SessionResumeNotAllowedException.class,
                () -> sessionService.resumeContext("session-1", "agent-1", Map.of("k", "v")));
    } finally {
        UserContextHolder.clear();
    }
}

@Test
void resumeContextShouldRejectSessionOfAnotherAgent() {
    SuperAgentContext existingContext = new SuperAgentContext();
    existingContext.setSessionId("session-1");
    existingContext.setUserId("user-1");
    existingContext.setAgentKey("agent-2");
    existingContext.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
    when(sessionContextStore.load("session-1")).thenReturn(Optional.of(existingContext));
    UserContextHolder.setUserId("user-1");

    try {
        assertThrows(SessionResumeNotAllowedException.class,
                () -> sessionService.resumeContext("session-1", "agent-1", Map.of("k", "v")));
    } finally {
        UserContextHolder.clear();
    }
}
```

- [ ] **Step 2: Run the core tests to verify the new service test fails**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=SuperAgentFactoryTest,SuperAgentSessionServiceTest test"
```

Expected: FAIL because `SuperAgentSessionService` does not exist yet.

- [ ] **Step 3: Introduce the focused session service and executor wrappers**

```java
// apex-agent/src/main/java/org/gemo/apex/core/SuperAgentExecutor.java
@Component
public class SuperAgentExecutor {

    private final SuperAgent superAgent;

    public SuperAgentExecutor(SuperAgent superAgent) {
        this.superAgent = superAgent;
    }

    public void execute(SuperAgentContext context) {
        superAgent.execute(context);
    }
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/core/SuperAgentSessionService.java
@Service
public class SuperAgentSessionService {

    // implement createContext(), validateExistingSessionForNewTurn(),
    // initializeNewTurn(), persistNewTurn(), and prepareRuntimeContext()
    // by moving the current `SuperAgentFactory` logic without behavior change
    //
    // implement resumeContext(sessionId, agentKey, humanResponse) with explicit failure:
    // return a valid context on success, otherwise throw SessionResumeNotAllowedException
    // when the session is not suspended, belongs to another user, or belongs to another agent
}
```

- [ ] **Step 4: Convert `SuperAgentFactory` into a compatibility facade**

```java
// apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java
@Service
public class SuperAgentFactory {

    private final SuperAgentSessionService sessionService;
    private final SuperAgentExecutor executor;

    public SuperAgentFactory(SuperAgentSessionService sessionService, SuperAgentExecutor executor) {
        this.sessionService = sessionService;
        this.executor = executor;
    }

    public SuperAgentContext createContext(String sessionId, String agentKey, String userQuery) {
        return sessionService.createContext(sessionId, agentKey, userQuery);
    }

    public SuperAgentContext resumeContext(String sessionId, String agentKey, Map<String, Object> humanResponse) {
        return sessionService.resumeContext(sessionId, agentKey, humanResponse);
    }

    public void executeContext(SuperAgentContext context) {
        executor.execute(context);
    }
}
```

- [ ] **Step 5: Switch `ChatStreamingApplicationService` to depend on the split services directly**

```java
// apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java
private final SuperAgentSessionService sessionService;
private final SuperAgentExecutor executor;

context = request.getType() == RequestType.HUMAN_RESPONSE
        ? sessionService.resumeContext(request.getSessionId(), request.getAgentKey(), request.getHumanResponse())
        : sessionService.createContext(request.getSessionId(), request.getAgentKey(), request.getQuery());

executor.execute(context);
```

Additional contract change for this task:

 - replace `resumeContext(...) == null` semantics with an explicit domain exception such as `SessionResumeNotAllowedException`
- reject resume when the suspended session belongs to another user or another agent
- remove orchestration-layer `null` translation once `ChatStreamingApplicationService` depends on `SuperAgentSessionService`

- [ ] **Step 6: Re-run the core and stream orchestration tests**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=SuperAgentFactoryTest,SuperAgentSessionServiceTest,ChatStreamingApplicationServiceTest test"
```

Expected: PASS with session lifecycle logic covered in `SuperAgentSessionServiceTest` and only minimal delegation left in `SuperAgentFactoryTest`.

- [ ] **Step 7: Commit the core split**

```bash
git add apex-agent/src/main/java/org/gemo/apex/core/SuperAgentSessionService.java apex-agent/src/main/java/org/gemo/apex/core/SuperAgentExecutor.java apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java apex-agent/src/main/java/org/gemo/apex/web/service/ChatStreamingApplicationService.java apex-agent/src/test/java/org/gemo/apex/core/SuperAgentSessionServiceTest.java apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java
git commit -m "refactor: split session lifecycle from agent execution"
```

## Task 5: Finish Frontend Terminal-State Integration and End-to-End Regression Coverage

**Files:**
- Modify: `apex-frontend/src/stores/session/store.test.ts`
- Modify: `apex-frontend/src/services/apex-api.test.ts`
- Modify: `apex-agent/src/test/java/org/gemo/apex/web/service/ChatStreamingApplicationServiceTest.java`

- [ ] **Step 1: Write the failing store test for failed stream termination**

```ts
it('marks the active session as error when the stream terminates with FAILED status', async () => {
  const envelopeSequence: SseEnvelope[] = [
    { event_type: 'STREAM_CONTENT', context: { mode: 'react', content_id: 'content-9' }, messages: [{ content: 'hello' }] },
    {
      event_type: 'END',
      context: {
        mode: 'react',
        execution_status: 'FAILED',
        error_code: 'STREAM_EXECUTION_FAILED',
        error_message: 'boom',
      },
      messages: [],
    },
  ]

  // existing store harness should consume envelopeSequence and expose final state
  expect(store.session.status).toBe('error')
})
```

- [ ] **Step 2: Run the frontend store and API tests to verify failure**

Run:

```bash
pwsh -NoLogo -Command "cmd /c npm run test:run -- src/stores/session/store.test.ts src/services/apex-api.test.ts"
```

Expected: FAIL until fixtures and stream assertions reflect the richer terminal contract.

- [ ] **Step 3: Update store-level fixtures and assertions for explicit terminal outcomes**

```ts
// apex-frontend/src/stores/session/store.test.ts
{
  event_type: 'END',
  context: { mode: 'react', execution_status: 'COMPLETED' },
  messages: [],
}
```

```ts
// add failed terminal fixture
{
  event_type: 'END',
  context: {
    mode: 'react',
    execution_status: 'FAILED',
    error_code: 'STREAM_EXECUTION_FAILED',
    error_message: 'boom',
  },
  messages: [],
}
```

- [ ] **Step 4: Add one backend service assertion that a failed stream still emits a terminal event before cleanup**

```java
verify(terminalEventFactory).buildForFailure(context, "STREAM_EXECUTION_FAILED", "boom");
assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
```

- [ ] **Step 5: Run the focused frontend and backend regression tests**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatStreamingApplicationServiceTest test"
pwsh -NoLogo -Command "cmd /c npm run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts src/services/apex-api.test.ts"
```

Expected: PASS with explicit success/failure/waiting end states covered on both sides.

- [ ] **Step 6: Run broader verification before merging**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=ChatControllerTest,ChatStreamingApplicationServiceTest,ChatTerminalEventFactoryTest,SuperAgentFactoryTest,SuperAgentSessionServiceTest test"
pwsh -NoLogo -Command "cmd /c npm run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts src/services/apex-api.test.ts"
pwsh -NoLogo -Command "cmd /c npm run build"
```

Expected: PASS for all targeted backend tests, frontend tests, and production build.

- [ ] **Step 7: Commit the terminal regression coverage**

```bash
git add apex-frontend/src/stores/session/store.test.ts apex-frontend/src/services/apex-api.test.ts apex-agent/src/test/java/org/gemo/apex/web/service/ChatStreamingApplicationServiceTest.java
git commit -m "test: cover explicit chat stream terminal states"
```

## Self-Review Checklist

- Spec coverage:
  - Phase 1 correctness is covered in Task 1 by changing both frontend handling and current backend `END` emission.
  - Controller slimming is covered in Task 2.
  - Spring-managed async execution and context propagation are covered in Task 3.
  - Splitting `SuperAgentFactory` into session and execution services, including resume ownership validation, is covered in Task 4.
  - Failure-explicit SSE protocol and frontend handling are covered in Tasks 1 and 5.
  - Failure before `SuperAgentContext` exists is covered in Tasks 2 and 3.
- Placeholder scan:
  - No `TODO`, `TBD`, or “handle appropriately” placeholders remain.
  - Logging verification, resume failure semantics, and transitional propagation steps are specified concretely.
  - Each task lists concrete files, commands, and expected outcomes.
- Type consistency:
  - Terminal context keys use `execution_status`, `error_code`, and `error_message` consistently across backend and frontend tasks.
  - Failure builders always force `execution_status=FAILED` instead of mirroring a stale context status.
  - Backend split uses `SuperAgentSessionService` and `SuperAgentExecutor` consistently.
