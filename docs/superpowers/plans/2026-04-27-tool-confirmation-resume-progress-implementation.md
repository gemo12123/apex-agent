# Tool Confirmation Resume Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change `TOOL_CONFIRMATION` resume behavior to skip every pre-hook that already completed before suspension, continue only the remaining pre-hooks after approval, and remove `hookSource` from the confirmation contract.

**Architecture:** Extend the pre-hook runtime so it returns explicit progress via `executedHookBeans`, persist that progress in `PendingToolExecution.executedPreHookBeans`, and feed the full list back into `SKIP_PRE_HOOK_BEANS` during resume. Remove `hookSource` from suspension state and confirmation SSE payloads so the new progress list is the only restore-time authority.

**Tech Stack:** Java 25, Spring Boot 3.4, Spring AI Alibaba agent framework, JUnit 5, Mockito, Vue 3, Pinia, Vitest, TypeScript

---

## File Map

**Backend runtime**

- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookResult.java`
  - Add `executedHookBeans` to the pre-hook runtime result so `CustomToolCallingManager` can persist hook progress.
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java`
  - Track completed pre-hook beans in order and return them on `REQUEST_CONFIRMATION`.
- Modify: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingToolExecution.java`
  - Replace `hookSource` with `executedPreHookBeans`.
- Modify: `apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java`
  - Persist the full executed pre-hook list when suspending for confirmation.
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java`
  - Resume using `executedPreHookBeans` as the only `SKIP_PRE_HOOK_BEANS` source.

**Backend confirmation contract cleanup**

- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationSpec.java`
  - Remove `hookSource`.
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHook.java`
  - Stop populating `hookSource` in generated confirmation specs.
- Modify: `apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java`
  - Stop serializing `hook_source` in confirmation SSE payloads.

**Backend tests**

- Modify: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`
  - Add assertions for ordered `executedHookBeans`.
- Modify: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
  - Verify suspended state stores `executedPreHookBeans`.
- Modify: `apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java`
  - Verify resume skips the full executed list and still merges `updated_args`.
- Modify: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`
  - Verify persisted confirmation state round-trips `executedPreHookBeans`.

**Frontend confirmation contract cleanup**

- Modify: `apex-frontend/src/types/apex.ts`
  - Remove `hook_source` and `hookSource` from tool confirmation types.
- Modify: `apex-frontend/src/stores/session/reducer.ts`
  - Stop mapping `hook_source` into client state.
- Modify: `apex-frontend/src/stores/session/reducer.test.ts`
  - Update confirmation SSE fixtures and state assertions.
- Modify: `apex-frontend/src/stores/session/store.test.ts`
  - Update tool confirmation fixtures used by the store.
- Modify: `apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts`
  - Remove obsolete `hookSource` fixture data.

### Task 1: Track Executed Pre-Hook Beans in the Runtime

**Files:**
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookResult.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`

- [ ] **Step 1: Write the failing runtime test**

```java
@Test
void runPreHooksShouldReturnExecutedHookBeansThroughConfirmationBoundary() {
    AgentHooksConfig config = AgentHooksConfig.builder()
            .preToolCall(List.of(
                    HookBindingConfig.builder().bean("mutateRoomHook").order(10).tools(List.of("meeting_tool")).build(),
                    HookBindingConfig.builder().bean("toolConfirmHook").order(20).tools(List.of("meeting_tool")).build(),
                    HookBindingConfig.builder().bean("lateHook").order(30).tools(List.of("meeting_tool")).build()))
            .build();

    when(agentWorkspaceService.getHooks("default_agent")).thenReturn(config);
    when(applicationContext.getBean("mutateRoomHook", PreToolCallHook.class))
            .thenReturn(context -> PreToolCallHookResult.proceedWithUpdatedArgs(Map.of(
                    "room", "B2001",
                    "date", "2026-04-22")));
    when(applicationContext.getBean("toolConfirmHook", PreToolCallHook.class))
            .thenReturn(context -> PreToolCallHookResult.requestConfirmation(ToolConfirmationSpec.builder()
                    .title("预订会议室前确认")
                    .toolDisplayName("会议室助手")
                    .build()));

    PreToolCallHookResult result = runtime.runPreHooks(PreToolCallHookContext.builder()
            .agentKey("default_agent")
            .toolName("meeting_tool")
            .arguments(new LinkedHashMap<>(Map.of("room", "A1001", "date", "2026-04-22")))
            .build());

    assertEquals(PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION, result.getOutcome());
    assertEquals(List.of("mutateRoomHook", "toolConfirmHook"), result.getExecutedHookBeans());
}
```

- [ ] **Step 2: Run the backend runtime test to verify it fails**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=DefaultAgentHookRuntimeTest test"
```

Expected: FAIL because `PreToolCallHookResult` does not expose `getExecutedHookBeans()` yet.

- [ ] **Step 3: Implement executed hook tracking in the runtime**

```java
// apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookResult.java
@Getter
@Builder
public class PreToolCallHookResult {

    private final Outcome outcome;
    private final Map<String, Object> updatedArgs;
    private final String blockReason;
    private final ToolConfirmationSpec confirmationSpec;
    private final List<String> executedHookBeans;

    public static PreToolCallHookResult proceedWithUpdatedArgs(Map<String, Object> updatedArgs) {
        return builder()
                .outcome(Outcome.PROCEED)
                .updatedArgs(updatedArgs)
                .executedHookBeans(List.of())
                .build();
    }

    public static PreToolCallHookResult requestConfirmation(ToolConfirmationSpec spec) {
        return builder()
                .outcome(Outcome.REQUEST_CONFIRMATION)
                .confirmationSpec(spec)
                .executedHookBeans(List.of())
                .build();
    }
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java
LinkedHashMap<String, Object> currentArguments = copyArguments(context);
List<String> executedHookBeans = new ArrayList<>();

for (HookBindingConfig binding : matchingBindings(hooks.getPreToolCall(), context.getToolName())) {
    if (skippedHookBeans.contains(binding.getBean())) {
        continue;
    }

    PreToolCallHookResult result = hook.apply(context.toBuilder()
            .arguments(new LinkedHashMap<>(currentArguments))
            .hookOptions(binding.getOptions())
            .build());

    if (result.getOutcome() == PreToolCallHookResult.Outcome.PROCEED) {
        if (result.getUpdatedArgs() != null) {
            currentArguments.clear();
            currentArguments.putAll(result.getUpdatedArgs());
        }
        executedHookBeans.add(binding.getBean());
        continue;
    }

    if (result.getOutcome() == PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION) {
        List<String> progress = new ArrayList<>(executedHookBeans);
        progress.add(binding.getBean());
        return PreToolCallHookResult.builder()
                .outcome(PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION)
                .updatedArgs(result.getUpdatedArgs() != null ? result.getUpdatedArgs() : new LinkedHashMap<>(currentArguments))
                .confirmationSpec(result.getConfirmationSpec())
                .executedHookBeans(progress)
                .build();
    }

    return result;
}

return PreToolCallHookResult.builder()
        .outcome(PreToolCallHookResult.Outcome.PROCEED)
        .updatedArgs(currentArguments)
        .executedHookBeans(List.copyOf(executedHookBeans))
        .build();
```

- [ ] **Step 4: Re-run the backend runtime test**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=DefaultAgentHookRuntimeTest test"
```

Expected: PASS with `DefaultAgentHookRuntimeTest` green.

- [ ] **Step 5: Commit the runtime progress tracking change**

```bash
git add apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookResult.java apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java
git commit -m "feat: track executed pre-hook beans"
```

### Task 2: Persist Executed Pre-Hook Progress Across Suspension and Resume

**Files:**
- Modify: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingToolExecution.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`

- [ ] **Step 1: Write the failing suspension and resume assertions**

```java
// apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java
when(hookRuntime.runPreHooks(any())).thenReturn(PreToolCallHookResult.builder()
        .outcome(PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION)
        .updatedArgs(Map.of("room", "B2001"))
        .confirmationSpec(ToolConfirmationSpec.builder()
                .confirmationId("confirm-1")
                .title("预订会议室前确认")
                .toolName("meeting_tool")
                .toolDisplayName("会议室助手")
                .build())
        .executedHookBeans(List.of("mutateRoomHook", "toolConfirmHook"))
        .build());

assertEquals(List.of("mutateRoomHook", "toolConfirmHook"),
        sessionContext.getPendingToolExecution().getExecutedPreHookBeans());
```

```java
// apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java
context.setPendingToolExecution(PendingToolExecution.builder()
        .toolCallId("call-1")
        .toolName("meeting_tool")
        .invocationId("invocation-1")
        .resolvedArguments(Map.of("room", "A1001", "date", "2026-04-22"))
        .editableFieldKeys(List.of("room"))
        .confirmationId("confirm-1")
        .executedPreHookBeans(List.of("mutateRoomHook", "toolConfirmHook"))
        .build());

verify(agentPromptAssembler).assembleToolExecutionPrompt(eq(context), argThat(extra ->
        List.of("mutateRoomHook", "toolConfirmHook").equals(extra.get(ToolContextKeys.SKIP_PRE_HOOK_BEANS))));
```

```java
// apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
context.setPendingToolExecution(PendingToolExecution.builder()
        .toolCallId("call-1")
        .toolName("meeting_tool")
        .invocationId("invocation-1")
        .resolvedArguments(Map.of("room", "A1001"))
        .editableFieldKeys(List.of("room"))
        .confirmationId("confirm-1")
        .executedPreHookBeans(List.of("mutateRoomHook", "toolConfirmHook"))
        .build());

assertEquals(List.of("mutateRoomHook", "toolConfirmHook"),
        loaded.getPendingToolExecution().getExecutedPreHookBeans());
```

- [ ] **Step 2: Run the focused backend confirmation tests to verify they fail**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=CustomToolCallingManagerTest,HumanInLoopResumerTest,InMemorySessionContextStoreTest test"
```

Expected: FAIL because `PendingToolExecution` still exposes `hookSource` instead of `executedPreHookBeans`.

- [ ] **Step 3: Implement persisted executed pre-hook progress**

```java
// apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingToolExecution.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingToolExecution {
    private String toolCallId;
    private String toolName;
    private String invocationId;
    private Map<String, Object> resolvedArguments;
    private List<String> editableFieldKeys;
    private String confirmationId;
    private List<String> executedPreHookBeans;
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java
if (preResult.getOutcome() == PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION) {
    suspendForConfirmation(sessionContext, toolCall, toolName, invocationId, resolvedArguments,
            preResult.getConfirmationSpec(), preResult.getExecutedHookBeans());
}

private void suspendForConfirmation(SuperAgentContext sessionContext,
        AssistantMessage.ToolCall toolCall,
        String toolName,
        String invocationId,
        Map<String, Object> resolvedArguments,
        ToolConfirmationSpec confirmationSpec,
        List<String> executedPreHookBeans) {
    sessionContext.setPendingToolExecution(PendingToolExecution.builder()
            .toolCallId(toolCall.id())
            .toolName(toolName)
            .invocationId(invocationId)
            .resolvedArguments(new LinkedHashMap<>(resolvedArguments))
            .editableFieldKeys(confirmationSpec.editableFieldKeys())
            .confirmationId(confirmationSpec.getConfirmationId())
            .executedPreHookBeans(executedPreHookBeans != null ? List.copyOf(executedPreHookBeans) : List.of())
            .build());
    // existing pending human interaction and SSE logic stays unchanged
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java
Prompt prompt = agentPromptAssembler.assembleToolExecutionPrompt(context, Map.of(
        ToolContextKeys.SKIP_PRE_HOOK_BEANS,
        pendingExecution.getExecutedPreHookBeans() != null
                ? List.copyOf(pendingExecution.getExecutedPreHookBeans())
                : List.of()));
```

- [ ] **Step 4: Re-run the focused backend confirmation tests**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=CustomToolCallingManagerTest,HumanInLoopResumerTest,InMemorySessionContextStoreTest test"
```

Expected: PASS with all three test classes green.

- [ ] **Step 5: Commit the suspension and resume progress change**

```bash
git add apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingToolExecution.java apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
git commit -m "feat: resume confirmations from pre-hook progress"
```

### Task 3: Remove `hookSource` from the Confirmation Contract

**Files:**
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationSpec.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHook.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java`
- Modify: `apex-frontend/src/types/apex.ts`
- Modify: `apex-frontend/src/stores/session/reducer.ts`
- Test: `apex-frontend/src/stores/session/reducer.test.ts`
- Test: `apex-frontend/src/stores/session/store.test.ts`
- Test: `apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts`

- [ ] **Step 1: Write the failing frontend and contract updates**

```ts
// apex-frontend/src/stores/session/reducer.test.ts
const envelope = {
  event_type: 'TOOL_CONFIRMATION',
  context: { mode: 'react', invocation_id: 'invocation-1' },
  messages: [{
    confirmation_id: 'confirm-1',
    tool_call_id: 'call-1',
    invocation_id: 'invocation-1',
    tool_name: 'meeting_tool',
    tool_display_name: '会议室助手',
    title: '预订会议室前确认',
    risk_level: 'MEDIUM',
    editable: true,
    confirm_label: '确认执行',
    deny_label: '取消',
    display_fields: [],
    editable_fields: [],
  }],
} satisfies ToolConfirmationEnvelope

expect(state.pendingConfirmations[0]).not.toHaveProperty('hookSource')
```

```ts
// apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts
confirmation: {
  id: 'call-1:confirm-1',
  confirmationId: 'confirm-1',
  toolCallId: 'call-1',
  invocationId: 'invocation-1',
  toolName: 'meeting_tool',
  toolDisplayName: '会议室助手',
  title: '预订会议室前确认',
  description: '请确认会议信息。',
  riskLevel: 'MEDIUM',
  editable: true,
  confirmLabel: '确认执行',
  denyLabel: '取消',
  displayFields: [{ key: 'room', label: '会议室', value: 'A1001', type: 'text' }],
  editableFields: [],
}
```

- [ ] **Step 2: Run the frontend tests to verify they fail**

Run:

```bash
pwsh -NoLogo -Command "npm --prefix apex-frontend run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts src/features/workspace/components/ToolConfirmationCard.test.ts"
```

Expected: FAIL because the TypeScript confirmation types and reducer still require `hook_source` / `hookSource`.

- [ ] **Step 3: Remove the obsolete contract field in backend and frontend**

```java
// apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationSpec.java
@Data
@Builder
public class ToolConfirmationSpec {
    private String confirmationId;
    private String title;
    private String description;
    private String toolName;
    private String toolDisplayName;
    private String riskLevel;
    private boolean editable;
    private String confirmLabel;
    private String denyLabel;
    @Builder.Default
    private List<ToolConfirmationDisplayField> displayFields = List.of();
    @Builder.Default
    private List<ToolConfirmationEditableField> editableFields = List.of();
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHook.java
return PreToolCallHookResult.requestConfirmation(ToolConfirmationSpec.builder()
        .confirmationId("confirm-" + context.getToolCallId())
        .title(title)
        .description(description)
        .toolName(context.getToolName())
        .toolDisplayName(displayName)
        .riskLevel(riskLevel)
        .editable(!editableFields.isEmpty())
        .confirmLabel(confirmLabel)
        .denyLabel(denyLabel)
        .displayFields(displayFields)
        .editableFields(editableFields)
        .build());
```

```java
// apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java
.messages(List.of(ToolConfirmationDetail.builder()
        .confirmationId(spec.getConfirmationId())
        .toolCallId(toolCall.id())
        .invocationId(invocationId)
        .toolName(spec.getToolName())
        .toolDisplayName(spec.getToolDisplayName())
        .title(spec.getTitle())
        .description(spec.getDescription())
        .riskLevel(spec.getRiskLevel())
        .editable(spec.isEditable())
        .confirmLabel(spec.getConfirmLabel())
        .denyLabel(spec.getDenyLabel())
        .displayFields(spec.getDisplayFields())
        .editableFields(spec.getEditableFields())
        .build()))
```

```ts
// apex-frontend/src/types/apex.ts
export interface ToolConfirmationDetail {
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

export interface ToolConfirmationRecord {
  id: string
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
}
```

```ts
// apex-frontend/src/stores/session/reducer.ts
return envelope.messages.map((message) => ({
  id: `${message.tool_call_id}:${message.confirmation_id}`,
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
}))
```

- [ ] **Step 4: Re-run the frontend tests and typecheck**

Run:

```bash
pwsh -NoLogo -Command "npm --prefix apex-frontend run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts src/features/workspace/components/ToolConfirmationCard.test.ts"
pwsh -NoLogo -Command "npm --prefix apex-frontend run typecheck"
```

Expected: PASS for both the focused Vitest run and `vue-tsc -b`.

- [ ] **Step 5: Commit the contract cleanup**

```bash
git add apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationSpec.java apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHook.java apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java apex-frontend/src/types/apex.ts apex-frontend/src/stores/session/reducer.ts apex-frontend/src/stores/session/reducer.test.ts apex-frontend/src/stores/session/store.test.ts apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts
git commit -m "refactor: remove hook source from confirmations"
```

### Task 4: Run End-to-End Focused Verification

**Files:**
- Modify: none
- Test: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`
- Test: `apex-frontend/src/stores/session/reducer.test.ts`
- Test: `apex-frontend/src/stores/session/store.test.ts`
- Test: `apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts`

- [ ] **Step 1: Run the backend verification suite**

Run:

```bash
pwsh -NoLogo -Command "mvn --% -f apex-agent/pom.xml -Dtest=DefaultAgentHookRuntimeTest,CustomToolCallingManagerTest,HumanInLoopResumerTest,InMemorySessionContextStoreTest test"
```

Expected: PASS with all four backend test classes green.

- [ ] **Step 2: Run the frontend verification suite**

Run:

```bash
pwsh -NoLogo -Command "npm --prefix apex-frontend run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts src/features/workspace/components/ToolConfirmationCard.test.ts"
pwsh -NoLogo -Command "npm --prefix apex-frontend run typecheck"
```

Expected: PASS with no failing tests and no type errors.

- [ ] **Step 3: Inspect the final diff**

Run:

```bash
pwsh -NoLogo -Command "git diff --stat"
```

Expected: only the runtime, contract, and test files listed in this plan are changed.

- [ ] **Step 4: Commit the verification checkpoint**

```bash
git add apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookResult.java apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingToolExecution.java apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationSpec.java apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHook.java apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java apex-frontend/src/types/apex.ts apex-frontend/src/stores/session/reducer.ts apex-frontend/src/stores/session/reducer.test.ts apex-frontend/src/stores/session/store.test.ts apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts
git commit -m "test: verify tool confirmation resume progress flow"
```

## Self-Review

**Spec coverage check**

- Checkpoint-resume semantics: covered by Task 1 and Task 2.
- `executedPreHookBeans` as the only restore input: covered by Task 2.
- Remove `hookSource` from confirmation contract: covered by Task 3.
- Focused runtime, persistence, and frontend verification: covered by Task 4.

**Placeholder scan**

- No `TODO`, `TBD`, or deferred implementation markers remain.
- Every code-changing step includes a concrete snippet.
- Every verification step includes an exact command and expected result.

**Type consistency**

- Backend naming is consistently `executedHookBeans` in `PreToolCallHookResult` and `executedPreHookBeans` in `PendingToolExecution`.
- Frontend confirmation types no longer mention `hook_source` or `hookSource`.
