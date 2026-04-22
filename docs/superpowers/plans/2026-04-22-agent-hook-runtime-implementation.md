# Agent Hook Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build agent-scoped pre/post tool hooks with global and per-agent config, dedicated confirmation cards with editable fields, persisted suspend/resume, and post-tool result replacement across backend and frontend.

**Architecture:** Add a dedicated backend `AgentHookRuntime` with explicit hook contracts and config resolution, then route non-`ask_human` tools through a per-tool-call coordinator so a hook can suspend mid-batch without losing completed tool results. Reuse the existing `HUMAN_RESPONSE` transport, but introduce a new `TOOL_CONFIRMATION` event plus persisted pending execution state so approval resumes the original tool call instead of asking the model to regenerate it.

**Tech Stack:** Java 17, Spring Boot, Spring AI, SnakeYAML, Jackson, Vue 3, Pinia, Vitest, JUnit 5, Mockito

---

## File Structure

**Backend config and hook contracts**

- Create: `apex-agent/src/main/java/org/gemo/apex/config/model/AgentHooksConfig.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/config/model/HookBindingConfig.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/config/model/AgentConfig.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/service/AgentWorkspaceService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/AgentHookRuntime.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/ToolMatcher.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/NoOpAgentHookRuntime.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PostToolCallHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookContext.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PostToolCallHookContext.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookResult.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PostToolCallHookResult.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationSpec.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationDisplayField.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationEditableField.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/EditableFieldInputType.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/PlainTextTruncateHook.java`

**Backend execution, persistence, and SSE**

- Create: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingHumanInteraction.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingToolExecution.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/InteractionType.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/core/engine/ToolExecutionCoordinator.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/constant/AgentEventType.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/constant/ToolContextKeys.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/message/AgentMessage.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/context/SuperAgentContext.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/model/SessionRuntimeSnapshot.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/AgentPromptAssembler.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/ToolCallProcessor.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java`

**Frontend session model and UI**

- Modify: `apex-frontend/src/types/apex.ts`
- Modify: `apex-frontend/src/stores/session/reducer.ts`
- Modify: `apex-frontend/src/stores/session/store.ts`
- Modify: `apex-frontend/src/features/workspace/presentation.ts`
- Modify: `apex-frontend/src/features/workspace/components/ChatPane.vue`
- Modify: `apex-frontend/src/features/workspace/components/ExecutionRail.vue`
- Modify: `apex-frontend/src/features/workspace/pages/WorkspacePage.vue`
- Create: `apex-frontend/src/features/workspace/components/ToolConfirmationCard.vue`

**Tests and docs**

- Create: `apex-agent/src/test/java/org/gemo/apex/service/AgentWorkspaceServiceTest.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHookTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/core/engine/ToolCallProcessorTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java`
- Modify: `apex-frontend/src/stores/session/reducer.test.ts`
- Modify: `apex-frontend/src/stores/session/store.test.ts`
- Create: `apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts`
- Create: `docs/superpowers/examples/agent-hook-config.md`

### Task 1: Normalize Hook Config and Precedence

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/config/model/AgentHooksConfig.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/config/model/HookBindingConfig.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/config/model/AgentConfig.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/service/AgentWorkspaceService.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/service/AgentWorkspaceServiceTest.java`

- [ ] **Step 1: Write the failing config precedence tests**

```java
@Test
void getHooksShouldFallBackToGlobalHooksWhenWorkspaceDoesNotDeclareHooks() {
    AgentConfig global = new AgentConfig();
    global.setAgentKey("default_agent");
    global.setHooks(AgentHooksConfig.builder()
            .preToolCall(List.of(HookBindingConfig.builder()
                    .bean("toolConfirmHook")
                    .enabled(true)
                    .tools(List.of("meeting_tool"))
                    .order(100)
                    .build()))
            .build());

    when(agentConfigProvider.getAgentConfig("default_agent")).thenReturn(global);
    when(resourceLoader.getResource("classpath:agents/default_agent/config.yml"))
            .thenReturn(resource("default-execution-mode: REACT\n"));

    AgentHooksConfig resolved = service.getHooks("default_agent");

    assertFalse(resolved.isDisabled());
    assertEquals(List.of("meeting_tool"), resolved.getPreToolCall().getFirst().getTools());
}

@Test
void getHooksShouldReplaceGlobalHooksWhenWorkspaceDeclaresHooksBlock() {
    AgentConfig global = new AgentConfig();
    global.setAgentKey("default_agent");
    global.setHooks(AgentHooksConfig.builder()
            .preToolCall(List.of(HookBindingConfig.builder().bean("globalHook").build()))
            .build());

    when(agentConfigProvider.getAgentConfig("default_agent")).thenReturn(global);
    when(resourceLoader.getResource("classpath:agents/default_agent/config.yml"))
            .thenReturn(resource("""
                    hooks:
                      pre-tool-call:
                        - bean: workspaceHook
                          enabled: true
                          tools: ["contacts_tool"]
                    """));

    AgentHooksConfig resolved = service.getHooks("default_agent");

    assertEquals("workspaceHook", resolved.getPreToolCall().getFirst().getBean());
    assertEquals(List.of("contacts_tool"), resolved.getPreToolCall().getFirst().getTools());
}

@Test
void getHooksShouldTreatHooksEmptyArrayAsDisableAll() {
    AgentConfig global = new AgentConfig();
    global.setAgentKey("default_agent");
    global.setHooks(AgentHooksConfig.builder()
            .preToolCall(List.of(HookBindingConfig.builder().bean("toolConfirmHook").build()))
            .build());

    when(agentConfigProvider.getAgentConfig("default_agent")).thenReturn(global);
    when(resourceLoader.getResource("classpath:agents/default_agent/config.yml"))
            .thenReturn(resource("hooks: []\n"));

    AgentHooksConfig resolved = service.getHooks("default_agent");

    assertTrue(resolved.isDisabled());
    assertTrue(resolved.getPreToolCall().isEmpty());
    assertTrue(resolved.getPostToolCall().isEmpty());
}
```

- [ ] **Step 2: Run the config test and verify it fails**

Run: `mvn -q "-Dtest=AgentWorkspaceServiceTest" test`

Expected: FAIL with compile errors such as `cannot find symbol: method getHooks(java.lang.String)` and missing `AgentHooksConfig`.

- [ ] **Step 3: Add the hook config model classes**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HookBindingConfig {

    private String bean;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private int order = 0;

    @Builder.Default
    private List<String> tools = List.of("*");

    @Builder.Default
    private Map<String, Object> options = Map.of();
}
```

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHooksConfig {

    @Builder.Default
    private List<HookBindingConfig> preToolCall = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> postToolCall = new ArrayList<>();

    @Builder.Default
    private boolean disabled = false;

    public static AgentHooksConfig disabled() {
        return AgentHooksConfig.builder().disabled(true).build();
    }

    public static AgentHooksConfig empty() {
        return AgentHooksConfig.builder().build();
    }
}
```

```java
@Data
public class AgentConfig {
    private String agentKey;
    private String name;
    private String description;
    private String url;
    private Integer timeout = 60000;
    private String workspace;
    private List<String> mcps;
    private List<String> subAgents;
    private List<String> skills;
    private ModeEnum defaultExecutionMode;
    private AgentHooksConfig hooks;
}
```

- [ ] **Step 4: Wire workspace parsing and merge semantics into `AgentWorkspaceService`**

```java
public AgentHooksConfig getHooks(String agentKey) {
    WorkspaceConfig workspaceConfig = getWorkspaceConfig(agentKey);
    if (workspaceConfig.isHooksConfigured()) {
        return workspaceConfig.getHooks();
    }

    AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
    return agentConfig != null && agentConfig.getHooks() != null
            ? agentConfig.getHooks()
            : AgentHooksConfig.empty();
}

@SuppressWarnings("unchecked")
private WorkspaceConfig loadWorkspaceConfig(String agentKey) {
    String configContent = readWorkspaceFile(agentKey, "config.yml");
    // existing parsing omitted

    if (map.containsKey("hooks")) {
        config.setHooksConfigured(true);
        config.setHooks(parseHooks(map.get("hooks")));
    }

    return config;
}

@SuppressWarnings("unchecked")
private AgentHooksConfig parseHooks(Object rawHooks) {
    if (rawHooks instanceof List<?> list && list.isEmpty()) {
        return AgentHooksConfig.disabled();
    }

    if (!(rawHooks instanceof Map<?, ?> hookMap)) {
        return AgentHooksConfig.empty();
    }

    return AgentHooksConfig.builder()
            .preToolCall(parseHookBindings(hookMap.get("pre-tool-call")))
            .postToolCall(parseHookBindings(hookMap.get("post-tool-call")))
            .disabled(false)
            .build();
}
```

```java
@Data
public static class WorkspaceConfig {
    private List<String> allowMcps = Collections.emptyList();
    private List<String> allowSubAgents = Collections.emptyList();
    private List<String> allowSkills = Collections.emptyList();
    private ModeEnum defaultExecutionMode;
    private boolean hooksConfigured = false;
    private AgentHooksConfig hooks = AgentHooksConfig.empty();
}
```

- [ ] **Step 5: Re-run the config test and verify it passes**

Run: `mvn -q "-Dtest=AgentWorkspaceServiceTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/config/model/AgentHooksConfig.java apex-agent/src/main/java/org/gemo/apex/config/model/HookBindingConfig.java apex-agent/src/main/java/org/gemo/apex/config/model/AgentConfig.java apex-agent/src/main/java/org/gemo/apex/service/AgentWorkspaceService.java apex-agent/src/test/java/org/gemo/apex/service/AgentWorkspaceServiceTest.java
git commit -m "feat: add agent hook config precedence"
```

### Task 2: Add Hook Contracts, Runtime, and Built-in Hook Beans

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/AgentHookRuntime.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/ToolMatcher.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/NoOpAgentHookRuntime.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PostToolCallHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookContext.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PostToolCallHookContext.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PreToolCallHookResult.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/PostToolCallHookResult.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationSpec.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationDisplayField.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/ToolConfirmationEditableField.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/EditableFieldInputType.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/hook/tool/builtin/PlainTextTruncateHook.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHookTest.java`

- [ ] **Step 1: Write the failing runtime tests**

```java
@Test
void runPreHooksShouldApplyMutationsInOrderAndStopOnConfirmation() {
    AgentHooksConfig config = AgentHooksConfig.builder()
            .preToolCall(List.of(
                    HookBindingConfig.builder().bean("mutateRoomHook").order(10).tools(List.of("meeting_tool")).build(),
                    HookBindingConfig.builder().bean("toolConfirmHook").order(20).tools(List.of("meeting_tool")).build()))
            .build();

    when(agentWorkspaceService.getHooks("default_agent")).thenReturn(config);
    when(applicationContext.getBean("mutateRoomHook")).thenReturn((PreToolCallHook) ctx ->
            PreToolCallHookResult.proceedWithUpdatedArgs(Map.of("room", "B2001", "date", "2026-04-22")));
    when(applicationContext.getBean("toolConfirmHook")).thenReturn((PreToolCallHook) ctx ->
            PreToolCallHookResult.requestConfirmation(ToolConfirmationSpec.builder()
                    .title("预订会议室前确认")
                    .toolDisplayName("会议室助手")
                    .build()));

    PreToolCallHookResult result = runtime.runPreHooks(PreToolCallHookContext.builder()
            .agentKey("default_agent")
            .toolName("meeting_tool")
            .arguments(new LinkedHashMap<>(Map.of("room", "A1001", "date", "2026-04-22")))
            .build());

    assertEquals(PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION, result.getOutcome());
    assertEquals("会议室助手", result.getConfirmationSpec().getToolDisplayName());
    assertEquals("预订会议室前确认", result.getConfirmationSpec().getTitle());
}

@Test
void runPostHooksShouldReplacePlainTextButKeepJson() {
    AgentHooksConfig config = AgentHooksConfig.builder()
            .postToolCall(List.of(HookBindingConfig.builder()
                    .bean("plainTextTruncateHook")
                    .order(100)
                    .tools(List.of("*"))
                    .options(Map.of("max-length", 8))
                    .build()))
            .build();

    when(agentWorkspaceService.getHooks("default_agent")).thenReturn(config);
    when(applicationContext.getBean("plainTextTruncateHook")).thenReturn(new PlainTextTruncateHook());

    PostToolCallHookResult textResult = runtime.runPostHooks(PostToolCallHookContext.builder()
            .agentKey("default_agent")
            .toolName("contacts_tool")
            .originalResult("very long plain text")
            .currentResult("very long plain text")
            .build());

    assertEquals(PostToolCallHookResult.Outcome.REPLACE_RESULT, textResult.getOutcome());
    assertTrue(textResult.getNextResult().contains("[truncated by post-hook"));
}
```

- [ ] **Step 2: Run the hook runtime tests and verify they fail**

Run: `mvn -q "-Dtest=DefaultAgentHookRuntimeTest,ToolConfirmHookTest" test`

Expected: FAIL with missing hook interfaces and runtime classes.

- [ ] **Step 3: Add the hook contracts and decision types**

```java
public interface PreToolCallHook {
    PreToolCallHookResult apply(PreToolCallHookContext context);
}

public interface PostToolCallHook {
    PostToolCallHookResult apply(PostToolCallHookContext context);
}
```

```java
@Data
@Builder(toBuilder = true)
public class PreToolCallHookContext {
    private String agentKey;
    private String sessionId;
    private String userId;
    private String toolCallId;
    private String invocationId;
    private String toolName;
    private String toolDescription;
    private String toolType;
    private String rawArguments;
    private Map<String, Object> arguments;
    private Map<String, Object> hookOptions;
    private Set<String> skippedHookBeans;
    private SuperAgentContext superAgentContext;
}
```

```java
@Data
@Builder
public class PostToolCallHookContext {
    private String agentKey;
    private String sessionId;
    private String userId;
    private String toolCallId;
    private String invocationId;
    private String toolName;
    private String rawArguments;
    private Map<String, Object> arguments;
    private Map<String, Object> hookOptions;
    private String originalResult;
    private String currentResult;
    private String hookSource;
    private SuperAgentContext superAgentContext;
}
```

```java
@Getter
@Builder
public class PreToolCallHookResult {

    public enum Outcome {
        PROCEED,
        BLOCK,
        REQUEST_CONFIRMATION
    }

    private final Outcome outcome;
    private final Map<String, Object> updatedArgs;
    private final String blockReason;
    private final ToolConfirmationSpec confirmationSpec;

    public static PreToolCallHookResult proceed() { return builder().outcome(Outcome.PROCEED).build(); }
    public static PreToolCallHookResult proceedWithUpdatedArgs(Map<String, Object> updatedArgs) {
        return builder().outcome(Outcome.PROCEED).updatedArgs(updatedArgs).build();
    }
    public static PreToolCallHookResult block(String reason) {
        return builder().outcome(Outcome.BLOCK).blockReason(reason).build();
    }
    public static PreToolCallHookResult requestConfirmation(ToolConfirmationSpec spec) {
        return builder().outcome(Outcome.REQUEST_CONFIRMATION).confirmationSpec(spec).build();
    }
}
```

```java
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
    private String hookSource;
    @Builder.Default
    private List<ToolConfirmationDisplayField> displayFields = List.of();
    @Builder.Default
    private List<ToolConfirmationEditableField> editableFields = List.of();

    public List<String> editableFieldKeys() {
        return editableFields.stream().map(ToolConfirmationEditableField::getKey).toList();
    }
}
```

```java
@Getter
@Builder
public class PostToolCallHookResult {

    public enum Outcome {
        KEEP,
        REPLACE_RESULT
    }

    private final Outcome outcome;
    private final String nextResult;

    public static PostToolCallHookResult keep() { return builder().outcome(Outcome.KEEP).build(); }
    public static PostToolCallHookResult replaceResult(String nextResult) {
        return builder().outcome(Outcome.REPLACE_RESULT).nextResult(nextResult).build();
    }
}
```

- [ ] **Step 4: Implement the runtime, tool matching, and built-in hooks**

```java
public interface AgentHookRuntime {

    PreToolCallHookResult runPreHooks(PreToolCallHookContext context);

    PostToolCallHookResult runPostHooks(PostToolCallHookContext context);
}
```

```java
@Component
public class NoOpAgentHookRuntime implements AgentHookRuntime {

    @Override
    public PreToolCallHookResult runPreHooks(PreToolCallHookContext context) {
        return PreToolCallHookResult.proceedWithUpdatedArgs(context.getArguments());
    }

    @Override
    public PostToolCallHookResult runPostHooks(PostToolCallHookContext context) {
        return PostToolCallHookResult.keep();
    }
}
```

```java
@Component
public class DefaultAgentHookRuntime implements AgentHookRuntime {

    private final AgentWorkspaceService agentWorkspaceService;
    private final ApplicationContext applicationContext;
    private final ToolMatcher toolMatcher;

    @Override
    public PreToolCallHookResult runPreHooks(PreToolCallHookContext context) {
        AgentHooksConfig hooks = agentWorkspaceService.getHooks(context.getAgentKey());
        if (hooks.isDisabled()) {
            return PreToolCallHookResult.proceed();
        }

        Map<String, Object> currentArgs = new LinkedHashMap<>(context.getArguments());
        for (HookBindingConfig binding : matchingBindings(hooks.getPreToolCall(), context.getToolName())) {
            if (context.getSkippedHookBeans().contains(binding.getBean())) {
                continue;
            }
            PreToolCallHook hook = applicationContext.getBean(binding.getBean(), PreToolCallHook.class);
            PreToolCallHookResult result = hook.apply(context.toBuilder()
                    .arguments(new LinkedHashMap<>(currentArgs))
                    .hookOptions(binding.getOptions())
                    .build());
            if (result.getOutcome() == PreToolCallHookResult.Outcome.PROCEED && result.getUpdatedArgs() != null) {
                currentArgs.clear();
                currentArgs.putAll(result.getUpdatedArgs());
                continue;
            }
            if (result.getOutcome() == PreToolCallHookResult.Outcome.PROCEED) {
                continue;
            }
            return result;
        }
        return PreToolCallHookResult.proceedWithUpdatedArgs(currentArgs);
    }
}
```

```java
@Component("toolConfirmHook")
public class ToolConfirmHook implements PreToolCallHook {

    @Override
    public PreToolCallHookResult apply(PreToolCallHookContext context) {
        List<ToolConfirmationDisplayField> displayFields = resolveDisplayFields(context);
        List<ToolConfirmationEditableField> editableFields = resolveEditableFields(context);

        return PreToolCallHookResult.requestConfirmation(ToolConfirmationSpec.builder()
                .confirmationId(UUID.randomUUID().toString())
                .title(option(context, "title", "工具调用确认"))
                .description(option(context, "description", "请确认是否继续执行该工具调用。"))
                .toolName(context.getToolName())
                .toolDisplayName(option(context, "tool-display-name", context.getToolName()))
                .riskLevel(option(context, "risk-level", "MEDIUM"))
                .editable(!editableFields.isEmpty())
                .confirmLabel(option(context, "confirm-label", "确认执行"))
                .denyLabel(option(context, "deny-label", "取消"))
                .hookSource("toolConfirmHook")
                .displayFields(displayFields)
                .editableFields(editableFields)
                .build());
    }
}
```

```java
@Component("plainTextTruncateHook")
public class PlainTextTruncateHook implements PostToolCallHook {

    @Override
    public PostToolCallHookResult apply(PostToolCallHookContext context) {
        String current = context.getCurrentResult();
        if (current == null || current.isBlank() || looksLikeJson(current)) {
            return PostToolCallHookResult.keep();
        }

        int maxLength = intOption(context.getHookOptions(), "max-length", 4000);
        if (current.length() <= maxLength) {
            return PostToolCallHookResult.keep();
        }

        String truncated = current.substring(0, maxLength)
                + "\n\n...[truncated by post-hook: "
                + context.getHookSource()
                + ", original_length="
                + current.length()
                + "]";
        return PostToolCallHookResult.replaceResult(truncated);
    }
}
```

- [ ] **Step 5: Re-run the runtime tests and verify they pass**

Run: `mvn -q "-Dtest=DefaultAgentHookRuntimeTest,ToolConfirmHookTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/hook apex-agent/src/main/java/org/gemo/apex/hook/tool apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java apex-agent/src/test/java/org/gemo/apex/hook/tool/builtin/ToolConfirmHookTest.java
git commit -m "feat: add agent hook runtime contracts"
```

### Task 3: Add Confirmation Message Models and Persisted Pending State

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingHumanInteraction.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/PendingToolExecution.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/domain/interaction/InteractionType.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/constant/AgentEventType.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/message/AgentMessage.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/context/SuperAgentContext.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/model/SessionRuntimeSnapshot.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`

- [ ] **Step 1: Write the failing persistence test**

```java
@Test
void saveAndLoadShouldPersistPendingConfirmationState() {
    SuperAgentContext context = buildContext();
    context.setPendingHumanInteraction(PendingHumanInteraction.builder()
            .interactionType(InteractionType.TOOL_CONFIRMATION.name())
            .toolCallId("call-1")
            .invocationId("invocation-1")
            .confirmationId("confirm-1")
            .build());
    context.setPendingToolExecution(PendingToolExecution.builder()
            .toolCallId("call-1")
            .toolName("meeting_tool")
            .invocationId("invocation-1")
            .resolvedArguments(Map.of("room", "A1001"))
            .editableFieldKeys(List.of("room"))
            .confirmationId("confirm-1")
            .hookSource("toolConfirmHook")
            .build());

    store.save(context);

    SuperAgentContext loaded = store.load("session-1").orElseThrow();

    assertEquals("TOOL_CONFIRMATION", loaded.getPendingHumanInteraction().getInteractionType());
    assertEquals("meeting_tool", loaded.getPendingToolExecution().getToolName());
    assertEquals(List.of("room"), loaded.getPendingToolExecution().getEditableFieldKeys());
}
```

- [ ] **Step 2: Run the persistence test and verify it fails**

Run: `mvn -q "-Dtest=InMemorySessionContextStoreTest" test`

Expected: FAIL with missing fields such as `getPendingHumanInteraction()` and `getPendingToolExecution()`.

- [ ] **Step 3: Add the pending interaction state and new SSE message**

```java
public enum InteractionType {
    ASK_HUMAN,
    TOOL_CONFIRMATION
}
```

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingHumanInteraction {
    private String interactionType;
    private String toolCallId;
    private String invocationId;
    private String confirmationId;
}
```

```java
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
    private String hookSource;
}
```

```java
public final class AgentEventType {
    public static final String STREAM_THINK = "STREAM_THINK";
    public static final String STREAM_CONTENT = "STREAM_CONTENT";
    public static final String PLAN_DECLARED = "PLAN_DECLARED";
    public static final String PLAN_CHANGE = "PLAN_CHANGE";
    public static final String INVOCATION_DECLARED = "INVOCATION_DECLARED";
    public static final String INVOCATION_CHANGE = "INVOCATION_CHANGE";
    public static final String TASK_THINK_DECLARED = "TASK_THINK_DECLARED";
    public static final String TASK_THINK_CHANGE = "TASK_THINK_CHANGE";
    public static final String ARTIFACT_DECLARED = "ARTIFACT_DECLARED";
    public static final String ARTIFACT_CHANGE = "ARTIFACT_CHANGE";
    public static final String END = "END";
    public static final String ASK_HUMAN = "ASK_HUMAN";
    public static final String TOOL_CONFIRMATION = "TOOL_CONFIRMATION";
}
```

```java
@JsonSubTypes({
        @JsonSubTypes.Type(value = AskHumanMessage.class, name = AgentEventType.ASK_HUMAN),
        @JsonSubTypes.Type(value = ToolConfirmationMessage.class, name = AgentEventType.TOOL_CONFIRMATION)
})
public abstract class AgentMessage {
    @JsonIgnore
    private String eventType;

    @JsonProperty("context")
    private Map<String, Object> context;
}
```

```java
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ToolConfirmationMessage extends AgentMessage {

    @JsonProperty("messages")
    private List<ToolConfirmationDetail> messages;

    public static ToolConfirmationMessage from(SuperAgentContext context,
            AssistantMessage.ToolCall toolCall,
            String invocationId,
            ToolConfirmationSpec spec) {
        return ToolConfirmationMessage.builder()
                .context(EngineContextHelper.buildMessageContext(
                        context,
                        ContextKeyEnum.EXECUTOR.getKey(), toolCall.name(),
                        ContextKeyEnum.INVOCATION_ID.getKey(), invocationId))
                .messages(List.of(ToolConfirmationDetail.builder()
                        .confirmationId(spec.getConfirmationId())
                        .toolCallId(toolCall.id())
                        .invocationId(invocationId)
                        .toolName(spec.getToolName())
                        .toolDisplayName(spec.getToolDisplayName())
                        .title(spec.getTitle())
                        .description(spec.getDescription())
                        .riskLevel(spec.getRiskLevel())
                        .hookSource(spec.getHookSource())
                        .editable(spec.isEditable())
                        .confirmLabel(spec.getConfirmLabel())
                        .denyLabel(spec.getDenyLabel())
                        .displayFields(spec.getDisplayFields())
                        .editableFields(spec.getEditableFields())
                        .build()))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolConfirmationDetail {
        @JsonProperty("confirmation_id")
        private String confirmationId;
        @JsonProperty("tool_call_id")
        private String toolCallId;
        @JsonProperty("invocation_id")
        private String invocationId;
        @JsonProperty("tool_name")
        private String toolName;
        @JsonProperty("tool_display_name")
        private String toolDisplayName;
        @JsonProperty("title")
        private String title;
        @JsonProperty("description")
        private String description;
        @JsonProperty("risk_level")
        private String riskLevel;
        @JsonProperty("hook_source")
        private String hookSource;
        @JsonProperty("editable")
        private boolean editable;
        @JsonProperty("confirm_label")
        private String confirmLabel;
        @JsonProperty("deny_label")
        private String denyLabel;
        @JsonProperty("display_fields")
        private List<ToolConfirmationDisplayField> displayFields;
        @JsonProperty("editable_fields")
        private List<ToolConfirmationEditableField> editableFields;
    }
}
```

- [ ] **Step 4: Persist the new fields in context snapshots**

```java
@Data
public class SessionRuntimeSnapshot {
    private String currentStageId;
    private Plan plan;
    private Map<String, Object> pendingToolResult;
    private PendingHumanInteraction pendingHumanInteraction;
    private PendingToolExecution pendingToolExecution;
}
```

```java
public static SessionRuntimeSnapshot toSnapshot(SuperAgentContext context) {
    SessionRuntimeSnapshot snapshot = new SessionRuntimeSnapshot();
    snapshot.setCurrentStageId(context.getCurrentStageId());
    snapshot.setPlan(context.getPlan());
    snapshot.setPendingToolResult(context.getPendingToolResult());
    snapshot.setPendingHumanInteraction(context.getPendingHumanInteraction());
    snapshot.setPendingToolExecution(context.getPendingToolExecution());
    return snapshot;
}
```

```java
if (snapshot != null) {
    context.setCurrentStageId(snapshot.getCurrentStageId());
    context.setPlan(snapshot.getPlan());
    context.setPendingToolResult(snapshot.getPendingToolResult());
    context.setPendingHumanInteraction(snapshot.getPendingHumanInteraction());
    context.setPendingToolExecution(snapshot.getPendingToolExecution());
}
```

- [ ] **Step 5: Re-run the persistence test and verify it passes**

Run: `mvn -q "-Dtest=InMemorySessionContextStoreTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/domain/interaction apex-agent/src/main/java/org/gemo/apex/message/ToolConfirmationMessage.java apex-agent/src/main/java/org/gemo/apex/constant/AgentEventType.java apex-agent/src/main/java/org/gemo/apex/message/AgentMessage.java apex-agent/src/main/java/org/gemo/apex/context/SuperAgentContext.java apex-agent/src/main/java/org/gemo/apex/memory/model/SessionRuntimeSnapshot.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
git commit -m "feat: persist tool confirmation state"
```

### Task 4: Integrate Hooks Into Tool Execution and Resume

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/core/engine/ToolExecutionCoordinator.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/constant/ToolContextKeys.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/AgentPromptAssembler.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/ToolCallProcessor.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/core/engine/ToolCallProcessorTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java`

- [ ] **Step 1: Write the failing execution-path tests**

```java
@Test
void executeToolCallsShouldSendToolConfirmationAndSuspendBeforeCallingRealTool() {
    AgentHookRuntime hookRuntime = Mockito.mock(AgentHookRuntime.class);
    ToolInvocationNotifier notifier = Mockito.mock(ToolInvocationNotifier.class);
    CustomToolCallingManager manager = CustomToolCallingManager.builder()
            .toolInvocationNotifier(notifier)
            .agentHookRuntime(hookRuntime)
            .build();

    ToolCallback toolCallback = mockedToolCallback("meeting_tool");
    when(toolCallback.call(any(String.class), any())).thenReturn("should-not-run");
    when(hookRuntime.runPreHooks(any())).thenReturn(PreToolCallHookResult.requestConfirmation(
            ToolConfirmationSpec.builder()
                    .confirmationId("confirm-1")
                    .title("预订会议室前确认")
                    .toolDisplayName("会议室助手")
                    .build()));

    SuperAgentContext sessionContext = new SuperAgentContext();
    sessionContext.setSseEmitter(new CapturingSseEmitter());
    Prompt prompt = promptWithTool(toolCallback, sessionContext);

    assertThrows(HumanInTheLoopException.class, () -> manager.executeToolCalls(prompt, chatResponse("meeting_tool")));
    verify(toolCallback, never()).call(any(String.class), any());
    assertEquals("TOOL_CONFIRMATION", sessionContext.getPendingHumanInteraction().getInteractionType());
}
```

```java
@Test
void processShouldAppendCompletedResponsesBeforeLaterToolSuspends() {
    AssistantMessage.ToolCall first = new AssistantMessage.ToolCall("call-1", "function", "contacts_tool", "{}");
    AssistantMessage.ToolCall second = new AssistantMessage.ToolCall("call-2", "function", "meeting_tool", "{}");

    when(agentToolExecutor.execute(any(Prompt.class), argThat(msg ->
            msg.getToolCalls().size() == 1 && "contacts_tool".equals(msg.getToolCalls().getFirst().name()))))
            .thenReturn(ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "contacts_tool", "done")))
                    .build());

    when(agentToolExecutor.execute(any(Prompt.class), argThat(msg ->
            msg.getToolCalls().size() == 1 && "meeting_tool".equals(msg.getToolCalls().getFirst().name()))))
            .thenThrow(new HumanInTheLoopException("waiting for confirmation"));

    assertThrows(HumanInTheLoopException.class, () ->
            toolCallProcessor.process(new Prompt(List.of()),
                    AssistantMessage.builder().toolCalls(List.of(first, second)).build(),
                    context,
                    SuperAgentContext.Stage.EXECUTION));

    verify(conversationMemoryManager).appendDialogueMessage(eq(context), argThat(response ->
            response.getResponses().stream().anyMatch(item -> "call-1".equals(item.id()))));
}
```

```java
@Test
void resumeShouldExecutePendingToolAfterApprovalWithoutReTriggeringSameHook() {
    context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
    context.setPendingHumanInteraction(PendingHumanInteraction.builder()
            .interactionType(InteractionType.TOOL_CONFIRMATION.name())
            .toolCallId("call-1")
            .invocationId("invocation-1")
            .confirmationId("confirm-1")
            .build());
    context.setPendingToolExecution(PendingToolExecution.builder()
            .toolCallId("call-1")
            .toolName("meeting_tool")
            .invocationId("invocation-1")
            .resolvedArguments(new LinkedHashMap<>(Map.of("room", "A1001", "date", "2026-04-22")))
            .editableFieldKeys(List.of("room"))
            .confirmationId("confirm-1")
            .hookSource("toolConfirmHook")
            .build());
    context.setPendingToolResult(Map.of("call-1", Map.of(
            "interaction_type", "TOOL_CONFIRMATION",
            "confirmation_id", "confirm-1",
            "decision", "APPROVE",
            "updated_args", Map.of("room", "B2001"))));

    when(agentPromptAssembler.assembleToolExecutionPrompt(eq(context), anyMap())).thenReturn(prompt);
    when(agentToolExecutor.execute(eq(prompt), any(AssistantMessage.class))).thenReturn(
            ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "meeting_tool", "approved")))
                    .build());

    humanInLoopResumer.resume(context);

    verify(agentPromptAssembler).assembleToolExecutionPrompt(eq(context), argThat(extra ->
            List.of("toolConfirmHook").equals(extra.get(ToolContextKeys.SKIP_PRE_HOOK_BEANS))));
    verify(agentToolExecutor).execute(eq(prompt), any(AssistantMessage.class));
}
```

- [ ] **Step 2: Run the execution-path tests and verify they fail**

Run: `mvn -q "-Dtest=CustomToolCallingManagerTest,ToolCallProcessorTest,HumanInLoopResumerTest,ChatControllerTest" test`

Expected: FAIL with missing builder method `agentHookRuntime`, missing `assembleToolExecutionPrompt`, and missing pending confirmation handling.

- [ ] **Step 3: Add prompt reconstruction and skip-hook context keys**

```java
public final class ToolContextKeys {

    public static final String SESSION_CONTEXT = "SESSION_CONTEXT";
    public static final String MCP_SESSION_CONTEXT = "MCP_SESSION_CONTEXT";
    public static final String CUSTOM_TOOL_METADATA = "CUSTOM_TOOL_METADATA";
    public static final String SKIP_PRE_HOOK_BEANS = "SKIP_PRE_HOOK_BEANS";

    private ToolContextKeys() {
    }
}
```

```java
public Prompt assembleToolExecutionPrompt(SuperAgentContext context, Map<String, Object> extraToolContext) {
    DashScopeChatOptions options = DashScopeChatOptions.builder()
            .withInternalToolExecutionEnabled(false)
            .withToolCallbacks(context.getAvailableTools())
            .withToolContext(buildToolContext(context, extraToolContext))
            .build();

    return new Prompt(conversationMemoryManager.buildModelMessages(context), options);
}

private Map<String, Object> buildToolContext(SuperAgentContext context, Map<String, Object> extraToolContext) {
    Map<String, Object> toolContext = new LinkedHashMap<>();
    toolContext.put(ToolContextKeys.SESSION_CONTEXT, context);
    toolContext.put(ToolContextKeys.MCP_SESSION_CONTEXT, buildMcpSessionContext(context));
    toolContext.putAll(extraToolContext);
    return Map.copyOf(toolContext);
}
```

- [ ] **Step 4: Integrate pre/post hooks and confirmation suspension into `CustomToolCallingManager`**

```java
String invocationId = "invocationId_" + IdUtil.fastSimpleUUID();
Map<String, Object> parsedArguments = JacksonUtils.fromJson(finalToolInputArguments, new TypeReference<>() {});
Set<String> skippedHookBeans = resolveSkippedHookBeans(toolContext);

PreToolCallHookResult preResult = agentHookRuntime.runPreHooks(PreToolCallHookContext.builder()
        .agentKey(sessionContext.getAgentKey())
        .sessionId(sessionContext.getSessionId())
        .userId(sessionContext.getUserId())
        .toolCallId(toolCall.id())
        .invocationId(invocationId)
        .toolName(toolName)
        .toolDescription(toolCallback.getToolDefinition().description())
        .rawArguments(finalToolInputArguments)
        .arguments(new LinkedHashMap<>(parsedArguments))
        .skippedHookBeans(skippedHookBeans)
        .superAgentContext(sessionContext)
        .build());

if (preResult.getOutcome() == PreToolCallHookResult.Outcome.BLOCK) {
    toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, preResult.getBlockReason()));
    continue;
}

if (preResult.getOutcome() == PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION) {
    sessionContext.setPendingHumanInteraction(PendingHumanInteraction.builder()
            .interactionType(InteractionType.TOOL_CONFIRMATION.name())
            .toolCallId(toolCall.id())
            .invocationId(invocationId)
            .confirmationId(preResult.getConfirmationSpec().getConfirmationId())
            .build());
    sessionContext.setPendingToolExecution(PendingToolExecution.builder()
            .toolCallId(toolCall.id())
            .toolName(toolName)
            .invocationId(invocationId)
            .resolvedArguments(new LinkedHashMap<>(parsedArguments))
            .editableFieldKeys(preResult.getConfirmationSpec().editableFieldKeys())
            .confirmationId(preResult.getConfirmationSpec().getConfirmationId())
            .hookSource(preResult.getConfirmationSpec().getHookSource())
            .build());
    sessionContext.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
    MessageUtils.sendMessage(sessionContext, ToolConfirmationMessage.from(sessionContext, toolCall, invocationId,
            preResult.getConfirmationSpec()));
    MessageUtils.sendMessage(sessionContext, InvocationChangeMessage.builder()
            .context(EngineContextHelper.buildMessageContext(sessionContext, ContextKeyEnum.INVOCATION_ID.getKey(), invocationId))
            .messages(List.of(InvocationChangeDetail.builder()
                    .changeType("STATUS_CHANGE")
                    .invocationId(invocationId)
                    .status("WAITING_CONFIRMATION")
                    .build()))
            .build());
    throw new HumanInTheLoopException("waiting for tool confirmation");
}

String resolvedArguments = JacksonUtils.toJson(
        preResult.getUpdatedArgs() != null ? preResult.getUpdatedArgs() : parsedArguments);
String toolCallResult = toolCallback.call(resolvedArguments, finalToolContext);
PostToolCallHookResult postResult = agentHookRuntime.runPostHooks(PostToolCallHookContext.builder()
        .agentKey(sessionContext.getAgentKey())
        .sessionId(sessionContext.getSessionId())
        .userId(sessionContext.getUserId())
        .toolCallId(toolCall.id())
        .invocationId(invocationId)
        .toolName(toolName)
        .rawArguments(resolvedArguments)
        .arguments(preResult.getUpdatedArgs() != null ? preResult.getUpdatedArgs() : parsedArguments)
        .originalResult(toolCallResult)
        .currentResult(toolCallResult)
        .superAgentContext(sessionContext)
        .build());
if (postResult.getOutcome() == PostToolCallHookResult.Outcome.REPLACE_RESULT) {
    toolCallResult = postResult.getNextResult();
}
```

```java
public final static class Builder {

    private ToolInvocationNotifier toolInvocationNotifier = new DefaultToolInvocationNotifier();
    private AgentHookRuntime agentHookRuntime = new NoOpAgentHookRuntime();

    public CustomToolCallingManager.Builder agentHookRuntime(AgentHookRuntime agentHookRuntime) {
        this.agentHookRuntime = agentHookRuntime;
        return this;
    }

    public CustomToolCallingManager build() {
        return new CustomToolCallingManager(
                this.observationRegistry,
                this.toolCallbackResolver,
                this.toolExecutionExceptionProcessor,
                this.toolInvocationNotifier,
                this.agentHookRuntime);
    }
}
```

- [ ] **Step 5: Execute other tools one-by-one so suspension preserves earlier results**

```java
@Component
public class ToolExecutionCoordinator {

    private final AgentToolExecutor agentToolExecutor;
    private final ConversationMemoryManager conversationMemoryManager;

    public void executeOtherTools(Prompt input, SuperAgentContext context, List<AssistantMessage.ToolCall> toolCalls) {
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            try {
                ToolResponseMessage responseMessage = agentToolExecutor.execute(input,
                        AssistantMessage.builder().toolCalls(List.of(toolCall)).build());
                conversationMemoryManager.appendDialogueMessage(context, responseMessage);
            } catch (HumanInTheLoopException ex) {
                throw ex;
            } catch (Exception ex) {
                conversationMemoryManager.appendDialogueMessage(context,
                        ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(
                                        toolCall.id(),
                                        toolCall.name(),
                                        "工具调用异常，请检查参数。错误: " + ex.getMessage())))
                                .build());
            }
        }
    }
}
```

```java
public class ToolCallProcessor {

    private final ToolExecutionCoordinator toolExecutionCoordinator;

    private void processOtherTools(Prompt input, SuperAgentContext context,
            List<AssistantMessage.ToolCall> otherToolCalls) {
        toolExecutionCoordinator.executeOtherTools(input, context, otherToolCalls);
    }
}
```

- [ ] **Step 6: Resume approved tool executions without re-triggering the same confirmation hook**

```java
if (InteractionType.TOOL_CONFIRMATION.name().equals(context.getPendingHumanInteraction().getInteractionType())) {
    Map<String, Object> submission = castSubmission(context.getPendingToolResult(), context.getPendingToolExecution().getToolCallId());
    String decision = String.valueOf(submission.getOrDefault("decision", "DENY"));

    if ("DENY".equalsIgnoreCase(decision)) {
        conversationMemoryManager.appendDialogueMessage(context, ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        context.getPendingToolExecution().getToolCallId(),
                        context.getPendingToolExecution().getToolName(),
                        "tool execution cancelled by user")))
                .build());
    } else {
        Map<String, Object> mergedArgs = new LinkedHashMap<>(context.getPendingToolExecution().getResolvedArguments());
        mergeEditableOverrides(mergedArgs,
                castMap(submission.get("updated_args")),
                Set.copyOf(context.getPendingToolExecution().getEditableFieldKeys()));

        Prompt prompt = agentPromptAssembler.assembleToolExecutionPrompt(context, Map.of(
                ToolContextKeys.SKIP_PRE_HOOK_BEANS, List.of(context.getPendingToolExecution().getHookSource())));

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        context.getPendingToolExecution().getToolCallId(),
                        "function",
                        context.getPendingToolExecution().getToolName(),
                        JacksonUtils.toJson(mergedArgs))))
                .build();

        ToolResponseMessage response = agentToolExecutor.execute(prompt, assistantMessage);
        conversationMemoryManager.appendDialogueMessage(context, response);
    }

    context.setPendingHumanInteraction(null);
    context.setPendingToolExecution(null);
    context.setPendingToolResult(null);
    context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
    return;
}
```

- [ ] **Step 7: Re-run the execution-path tests and verify they pass**

Run: `mvn -q "-Dtest=CustomToolCallingManagerTest,ToolCallProcessorTest,HumanInLoopResumerTest,ChatControllerTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/constant/ToolContextKeys.java apex-agent/src/main/java/org/gemo/apex/core/engine/AgentPromptAssembler.java apex-agent/src/main/java/org/gemo/apex/component/CustomToolCallingManager.java apex-agent/src/main/java/org/gemo/apex/core/engine/ToolExecutionCoordinator.java apex-agent/src/main/java/org/gemo/apex/core/engine/ToolCallProcessor.java apex-agent/src/main/java/org/gemo/apex/core/engine/HumanInLoopResumer.java apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java apex-agent/src/test/java/org/gemo/apex/core/engine/ToolCallProcessorTest.java apex-agent/src/test/java/org/gemo/apex/core/engine/HumanInLoopResumerTest.java apex-agent/src/test/java/org/gemo/apex/web/controller/ChatControllerTest.java
git commit -m "feat: wire tool hook execution flow"
```

### Task 5: Extend Frontend Session State and Resume Payloads

**Files:**
- Modify: `apex-frontend/src/types/apex.ts`
- Modify: `apex-frontend/src/stores/session/reducer.ts`
- Modify: `apex-frontend/src/stores/session/store.ts`
- Modify: `apex-frontend/src/features/workspace/presentation.ts`
- Modify: `apex-frontend/src/features/workspace/pages/WorkspacePage.vue`
- Modify: `apex-frontend/src/stores/session/reducer.test.ts`
- Modify: `apex-frontend/src/stores/session/store.test.ts`

- [ ] **Step 1: Write the failing frontend state tests**

```ts
it('stores tool confirmations separately and keeps END in waiting-confirmation state', () => {
  let state = createSessionViewModel()

  state = applyEnvelope(state, {
    event_type: 'TOOL_CONFIRMATION',
    context: { mode: 'react', stage_id: 'react-stage', executor: 'meeting_tool' },
    messages: [
      {
        confirmation_id: 'confirm-1',
        tool_call_id: 'call-1',
        invocation_id: 'invocation-1',
        tool_name: 'meeting_tool',
        tool_display_name: '会议室助手',
        title: '预订会议室前确认',
        description: '请确认会议信息。',
        risk_level: 'MEDIUM',
        hook_source: 'toolConfirmHook',
        editable: true,
        confirm_label: '确认执行',
        deny_label: '取消',
        display_fields: [{ key: 'room', label: '会议室', value: 'A1001', type: 'text' }],
        editable_fields: [
          { key: 'room', label: '会议室', input_type: 'single-select', value: 'A1001', required: true },
        ],
      },
    ],
  } satisfies SseEnvelope)

  state = applyEnvelope(state, {
    event_type: 'END',
    context: { mode: 'react' },
    messages: [],
  } satisfies SseEnvelope)

  expect(state.status).toBe('waiting-confirmation')
  expect(state.pendingConfirmations).toHaveLength(1)
  expect(state.pendingPrompts).toHaveLength(0)
})
```

```ts
it('submits confirmation approval payloads with edited args', async () => {
  let resumePayload: ChatRequest['humanResponse']
  const mockClient: ApexApiClient = {
    async fetchAgents() {
      return [{ agentKey: 'default_agent', name: 'Default Agent' }]
    },
    async streamChat(request, _userId, _signal, onEnvelope) {
      if (request.type === 'NEW') {
        onEnvelope({
          event_type: 'TOOL_CONFIRMATION',
          context: { mode: 'react' },
          messages: [
            {
              confirmation_id: 'confirm-1',
              tool_call_id: 'call-1',
              invocation_id: 'invocation-1',
              tool_name: 'meeting_tool',
              tool_display_name: '会议室助手',
              title: '预订会议室前确认',
              description: '请确认会议信息。',
              risk_level: 'MEDIUM',
              hook_source: 'toolConfirmHook',
              editable: true,
              confirm_label: '确认执行',
              deny_label: '取消',
              display_fields: [],
              editable_fields: [
                { key: 'room', label: '会议室', input_type: 'text', value: 'A1001', required: true },
              ],
            },
          ],
        })
        onEnvelope({ event_type: 'END', context: { mode: 'react' }, messages: [] })
        return
      }

      resumePayload = request.humanResponse
      onEnvelope({ event_type: 'END', context: { mode: 'react' }, messages: [] })
    },
  }

  setActivePinia(createPinia())
  setApexApiClientForTesting(mockClient)

  const store = useSessionStore()
  await store.initialize()
  await store.sendPrompt('book the room')
  await store.submitConfirmation(store.session.pendingConfirmations[0], 'APPROVE', { room: 'B2001' })

  expect(resumePayload).toEqual({
    'call-1': {
      interaction_type: 'TOOL_CONFIRMATION',
      confirmation_id: 'confirm-1',
      decision: 'APPROVE',
      updated_args: { room: 'B2001' },
    },
  })
})
```

- [ ] **Step 2: Run the frontend state tests and verify they fail**

Run: `cmd /c npm run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts`

Expected: FAIL with unknown event type `TOOL_CONFIRMATION`, missing `pendingConfirmations`, and missing `submitConfirmation`.

- [ ] **Step 3: Extend the transport and state types**

```ts
export interface ToolConfirmationDisplayField {
  key: string
  label: string
  value: string | number | boolean
  type: 'text'
}

export interface ToolConfirmationEditableField {
  key: string
  label: string
  input_type: 'text' | 'textarea' | 'single-select' | 'confirm' | 'date' | 'datetime'
  value: string | number | boolean
  required?: boolean
  options?: AskHumanOption[]
}

export interface ToolConfirmationDetail {
  confirmation_id: string
  tool_call_id: string
  invocation_id: string
  tool_name: string
  tool_display_name: string
  title: string
  description?: string
  risk_level: string
  hook_source: string
  editable: boolean
  confirm_label: string
  deny_label: string
  display_fields: ToolConfirmationDisplayField[]
  editable_fields: ToolConfirmationEditableField[]
}

export type ToolConfirmationEnvelope = SseEnvelopeBase<'TOOL_CONFIRMATION', ToolConfirmationDetail>
```

```ts
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
  hookSource: string
  editable: boolean
  confirmLabel: string
  denyLabel: string
  displayFields: ToolConfirmationDisplayField[]
  editableFields: ToolConfirmationEditableField[]
}

export type HumanResponseEntry =
  | { interaction_type: 'ASK_HUMAN'; answers: Record<string, string | string[]> }
  | {
      interaction_type: 'TOOL_CONFIRMATION'
      confirmation_id: string
      decision: 'APPROVE' | 'DENY'
      updated_args?: Record<string, unknown>
    }

export interface SessionViewModel {
  sessionId: string | null
  agentKey: string | null
  status:
    | 'idle'
    | 'streaming'
    | 'waiting-human'
    | 'waiting-confirmation'
    | 'completed'
    | 'aborted'
    | 'error'
  messages: MessageRecord[]
  stages: StageRecord[]
  globalArtifacts: ArtifactRecord[]
  pendingPrompts: HumanPromptRecord[]
  pendingConfirmations: ToolConfirmationRecord[]
}
```

- [ ] **Step 4: Teach the reducer and store about confirmations**

```ts
export function createToolConfirmationRecords(
  envelope: ToolConfirmationEnvelope,
): ToolConfirmationRecord[] {
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
    hookSource: message.hook_source,
    editable: message.editable,
    confirmLabel: message.confirm_label,
    denyLabel: message.deny_label,
    displayFields: message.display_fields,
    editableFields: message.editable_fields,
  }))
}

case 'TOOL_CONFIRMATION':
  nextState.pendingConfirmations = createToolConfirmationRecords(envelope)
  nextState.pendingPrompts = []
  nextState.status = 'waiting-confirmation'
  return nextState

case 'END':
  nextState.status = nextState.pendingConfirmations.length > 0
    ? 'waiting-confirmation'
    : nextState.pendingPrompts.some((prompt) => !prompt.answered)
      ? 'waiting-human'
      : 'completed'
  return nextState
```

```ts
async function submitConfirmation(
  confirmation: ToolConfirmationRecord,
  decision: 'APPROVE' | 'DENY',
  updatedArgs: Record<string, unknown> = {},
): Promise<void> {
  const sessionId = session.value.sessionId ?? crypto.randomUUID()
  session.value.sessionId = sessionId
  session.value.pendingConfirmations = []
  errorMessage.value = ''

  await runChat({
    sessionId,
    query: '',
    type: 'HUMAN_RESPONSE',
    agentKey: selectedAgentKey.value,
    humanResponse: {
      [confirmation.toolCallId]: {
        interaction_type: 'TOOL_CONFIRMATION',
        confirmation_id: confirmation.confirmationId,
        decision,
        ...(decision === 'APPROVE' && Object.keys(updatedArgs).length > 0
          ? { updated_args: updatedArgs }
          : {}),
      },
    },
  })
}
```

- [ ] **Step 5: Update presentation labels and workspace wiring**

```ts
const sessionStatusLabels: Record<SessionViewModel['status'], string> = {
  idle: '待开始',
  streaming: '处理中',
  'waiting-human': '等待确认',
  'waiting-confirmation': '等待工具确认',
  completed: '已完成',
  aborted: '已停止',
  error: '异常',
}
```

```vue
function handleToolConfirmation(payload: {
  confirmation: (typeof session.value.pendingConfirmations)[number]
  decision: 'APPROVE' | 'DENY'
  updatedArgs?: Record<string, unknown>
}): void {
  void sessionStore.submitConfirmation(payload.confirmation, payload.decision, payload.updatedArgs ?? {})
}
```

- [ ] **Step 6: Re-run the frontend state tests and verify they pass**

Run: `cmd /c npm run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts`

Expected: PASS with `2 passed`.

- [ ] **Step 7: Commit**

```bash
git add apex-frontend/src/types/apex.ts apex-frontend/src/stores/session/reducer.ts apex-frontend/src/stores/session/store.ts apex-frontend/src/features/workspace/presentation.ts apex-frontend/src/features/workspace/pages/WorkspacePage.vue apex-frontend/src/stores/session/reducer.test.ts apex-frontend/src/stores/session/store.test.ts
git commit -m "feat: add tool confirmation session state"
```

### Task 6: Build the Editable Confirmation Card, Update the Rail, and Verify End-to-End

**Files:**
- Create: `apex-frontend/src/features/workspace/components/ToolConfirmationCard.vue`
- Create: `apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts`
- Modify: `apex-frontend/src/features/workspace/components/ChatPane.vue`
- Modify: `apex-frontend/src/features/workspace/components/ExecutionRail.vue`
- Create: `docs/superpowers/examples/agent-hook-config.md`

- [ ] **Step 1: Write the failing component test**

```ts
it('shows summary first, opens edit mode, and submits edited args', async () => {
  const wrapper = mount(ToolConfirmationCard, {
    props: {
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
        hookSource: 'toolConfirmHook',
        editable: true,
        confirmLabel: '确认执行',
        denyLabel: '取消',
        displayFields: [{ key: 'room', label: '会议室', value: 'A1001', type: 'text' }],
        editableFields: [
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
    },
  })

  expect(wrapper.text()).toContain('预订会议室前确认')
  expect(wrapper.text()).toContain('会议室助手')
  expect(wrapper.find('select').exists()).toBe(false)

  await wrapper.get('[data-testid="edit-button"]').trigger('click')
  await wrapper.get('select').setValue('B2001')
  await wrapper.get('[data-testid="approve-button"]').trigger('click')

  expect(wrapper.emitted('submit')?.[0]).toEqual([
    { decision: 'APPROVE', updatedArgs: { room: 'B2001' } },
  ])
})
```

- [ ] **Step 2: Run the component test and verify it fails**

Run: `cmd /c npm run test:run -- src/features/workspace/components/ToolConfirmationCard.test.ts`

Expected: FAIL with `Cannot find module '@/features/workspace/components/ToolConfirmationCard.vue'`.

- [ ] **Step 3: Build the new card component with summary-first UX and edit button**

```vue
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { ToolConfirmationRecord } from '@/types/apex'

const props = defineProps<{ confirmation: ToolConfirmationRecord }>()
const emit = defineEmits<{
  (event: 'submit', payload: { decision: 'APPROVE' | 'DENY'; updatedArgs?: Record<string, unknown> }): void
}>()

const editing = ref(false)
const formState = reactive(
  Object.fromEntries(props.confirmation.editableFields.map((field) => [field.key, field.value])),
)

const canApprove = computed(() =>
  props.confirmation.editableFields.every((field) => !field.required || String(formState[field.key] ?? '').trim().length > 0),
)

function approve(): void {
  emit('submit', {
    decision: 'APPROVE',
    updatedArgs: editing.value ? { ...formState } : {},
  })
}

function deny(): void {
  emit('submit', { decision: 'DENY' })
}
</script>
```

```vue
<template>
  <article class="tool-confirmation-card">
    <header class="tool-confirmation-card__header">
      <p class="tool-confirmation-card__eyebrow">{{ confirmation.toolDisplayName }}</p>
      <h3 class="tool-confirmation-card__title">{{ confirmation.title }}</h3>
      <p v-if="confirmation.description" class="tool-confirmation-card__description">
        {{ confirmation.description }}
      </p>
    </header>

    <dl class="tool-confirmation-card__summary">
      <div v-for="field in confirmation.displayFields" :key="field.key">
        <dt>{{ field.label }}</dt>
        <dd>{{ field.value }}</dd>
      </div>
    </dl>

    <button
      v-if="confirmation.editable"
      data-testid="edit-button"
      class="ghost-button"
      type="button"
      @click="editing = !editing"
    >
      编辑参数
    </button>

    <div v-if="editing" class="tool-confirmation-card__form">
      <label v-for="field in confirmation.editableFields" :key="field.key">
        <span>{{ field.label }}</span>
        <select v-if="field.input_type === 'single-select'" v-model="formState[field.key]">
          <option v-for="option in field.options ?? []" :key="option.label" :value="option.label">
            {{ option.label }}
          </option>
        </select>
        <textarea v-else-if="field.input_type === 'textarea'" v-model="formState[field.key]" rows="3" />
        <input v-else-if="field.input_type === 'date'" v-model="formState[field.key]" type="date" />
        <input v-else-if="field.input_type === 'datetime'" v-model="formState[field.key]" type="datetime-local" />
        <input v-else-if="field.input_type === 'confirm'" v-model="formState[field.key]" type="checkbox" />
        <input v-else v-model="formState[field.key]" type="text" />
      </label>
    </div>

    <footer class="tool-confirmation-card__actions">
      <button class="ghost-button" type="button" @click="deny">{{ confirmation.denyLabel }}</button>
      <button
        data-testid="approve-button"
        class="accent-button"
        type="button"
        :disabled="!canApprove"
        @click="approve"
      >
        {{ confirmation.confirmLabel }}
      </button>
    </footer>
  </article>
</template>
```

- [ ] **Step 4: Render confirmation cards in the chat pane and show waiting status in the rail**

```vue
<ToolConfirmationCard
  v-for="confirmation in props.pendingConfirmations"
  :key="confirmation.id"
  :confirmation="confirmation"
  @submit="emit('submit-confirmation', { confirmation, ...$event })"
/>
```

```vue
const props = defineProps<{
  messages: MessageRecord[]
  pendingPrompts: HumanPromptRecord[]
  pendingConfirmations: ToolConfirmationRecord[]
  status: SessionViewModel['status']
}>()
```

```vue
<button
  v-for="invocation in stage.invocations"
  :key="invocation.id"
  class="rail-button"
  :class="{ 'rail-button--active': selectedInvocationId === invocation.id }"
  type="button"
  @click="emit('select-invocation', { stageId: stage.id, invocationId: invocation.id })"
>
  <strong>{{ invocation.name }}</strong>
  <span>{{ invocation.invocationType }} · {{ formatRuntimeStatus(invocation.status) }}</span>
</button>
```

- [ ] **Step 5: Add operator-facing config examples**

````md
# Agent Hook Config Examples

## Global config in `application.yml`

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
              tools: ["meeting_tool"]
              options:
                title: "预订会议室前确认"
                tool-display-name: "会议室助手"
                confirm-label: "确认执行"
                deny-label: "取消"
                display-fields:
                  - key: room
                    label: "会议室"
                editable-fields:
                  - key: room
                    label: "会议室"
                    input-type: "single-select"
          post-tool-call:
            - bean: plainTextTruncateHook
              enabled: true
              order: 200
              tools: ["*"]
              options:
                max-length: 4000
```

## Workspace override in `agents/<agentKey>/config.yml`

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
```

## Disable all hooks for one agent

```yaml
hooks: []
```
````

- [ ] **Step 6: Run the focused frontend tests and builds**

Run: `cmd /c npm run test:run -- src/stores/session/reducer.test.ts src/stores/session/store.test.ts src/features/workspace/components/ToolConfirmationCard.test.ts`

Expected: PASS with all selected Vitest specs green.

Run: `cmd /c npm run build`

Expected: frontend build completes with Vite success output such as `built in`.

Run: `mvn -q "-Dtest=AgentWorkspaceServiceTest,DefaultAgentHookRuntimeTest,CustomToolCallingManagerTest,ToolCallProcessorTest,HumanInLoopResumerTest,ChatControllerTest,InMemorySessionContextStoreTest" test`

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add apex-frontend/src/features/workspace/components/ToolConfirmationCard.vue apex-frontend/src/features/workspace/components/ToolConfirmationCard.test.ts apex-frontend/src/features/workspace/components/ChatPane.vue apex-frontend/src/features/workspace/components/ExecutionRail.vue docs/superpowers/examples/agent-hook-config.md
git commit -m "feat: add editable tool confirmation card"
```
