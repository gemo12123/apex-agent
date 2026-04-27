# Agent Session Fixed Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the latest `fixedMessages` snapshot on `agent_session` and reuse it when assembling memory extraction inputs without changing per-round runtime prompt generation.

**Architecture:** Store `fixedMessages` as a JSON snapshot in `agent_session.fixed_messages`, round-trip it through `AgentSessionEntity` and `SessionContextEntityConverter`, and keep `SessionContextStore.loadAllRawDialogueMessages()` scoped to dialogue only. Build extraction inputs in `MemoryLifecycleManager` by prepending fixed messages to raw dialogue messages so background extraction and compaction pre-flush share one consistent assembly rule.

**Tech Stack:** Java 25, Spring Boot 3.4, Spring AI message model, MyBatis Plus, Jackson, JUnit 5, Mockito, Maven Surefire

---

### Task 1: Persist fixed message snapshots in session metadata

**Files:**
- Modify: `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionEntity.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/MessageEntityConverter.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverterTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`

- [ ] **Step 1: Write the failing persistence tests**

```java
@Test
void fromEntitiesShouldRestorePersistedFixedMessages() {
    AgentSessionEntity sessionEntity = new AgentSessionEntity();
    sessionEntity.setSessionId("session-1");
    sessionEntity.setAgentKey("agent-1");
    sessionEntity.setUserId("user-1");
    sessionEntity.setCurrentStage("EXECUTION");
    sessionEntity.setExecutionMode(ModeEnum.REACT.name());
    sessionEntity.setExecutionStatus(ExecutionStatus.IN_PROGRESS.name());
    sessionEntity.setFixedMessages(JacksonUtils.toJson(List.of(
            new SystemMessage("stage-system-prompt"),
            new UserMessage("Current user id: user-1"))));

    SuperAgentContext context = SessionContextEntityConverter.fromEntities(sessionEntity, null, List.of(), 0);

    assertEquals(2, context.getFixedMessages().size());
    assertInstanceOf(SystemMessage.class, context.getFixedMessages().get(0));
    assertEquals("stage-system-prompt", context.getFixedMessages().get(0).getText());
    assertInstanceOf(UserMessage.class, context.getFixedMessages().get(1));
}
```

```java
assertNotNull(storedSessionEntity.getFixedMessages());
assertEquals(1, loaded.getFixedMessages().size());
assertInstanceOf(SystemMessage.class, loaded.getFixedMessages().getFirst());
assertEquals("fixed-1", loaded.getFixedMessages().getFirst().getText());
```

- [ ] **Step 2: Run the targeted tests to confirm they fail**

Run:

```bash
mvn -q -f apex-agent/pom.xml -Dtest=SessionContextEntityConverterTest,InMemorySessionContextStoreTest test
```

Expected:

- `SessionContextEntityConverterTest` fails because `fixedMessages` is not restored from `AgentSessionEntity`
- `InMemorySessionContextStoreTest` fails because loaded contexts still return an empty `fixedMessages` list

- [ ] **Step 3: Implement `fixed_messages` persistence and round-trip restore**

```sql
CREATE TABLE IF NOT EXISTS agent_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    agent_key VARCHAR(64) NOT NULL,
    execution_status VARCHAR(32),
    current_stage VARCHAR(32),
    execution_mode VARCHAR(32),
    last_active_time TIMESTAMP,
    runtime_snapshot TEXT,
    fixed_messages TEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);
```

```java
@TableField("fixed_messages")
private String fixedMessages;
```

```java
public static String toPayloadList(List<Message> messages) {
    return JacksonUtils.toJson(messages != null ? messages : List.of());
}

public static List<Message> fromPayloadList(String payload) {
    List<Message> messages = JacksonUtils.fromJson(payload, new TypeReference<List<Message>>() {});
    return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
}
```

```java
entity.setFixedMessages(MessageEntityConverter.toPayloadList(context.getFixedMessages()));
```

```java
context.setFixedMessages(MessageEntityConverter.fromPayloadList(sessionEntity.getFixedMessages()));
```

- [ ] **Step 4: Re-run the targeted tests and verify they pass**

Run:

```bash
mvn -q -f apex-agent/pom.xml -Dtest=SessionContextEntityConverterTest,InMemorySessionContextStoreTest test
```

Expected:

- `BUILD SUCCESS`
- the converter test proves fixed message roles and text survive the JSON round-trip
- the in-memory store test proves save/load semantics now match the JDBC contract for `fixedMessages`

- [ ] **Step 5: Commit the persistence slice**

```bash
git add apex-agent/src/main/resources/db/memory-schema-postgresql.sql apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionEntity.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/MessageEntityConverter.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java apex-agent/src/test/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverterTest.java apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
git commit -m "feat: persist fixed message snapshots"
```

### Task 2: Prepend fixed messages when building memory extraction inputs

**Files:**
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/write/MemoryLifecycleManager.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/extract/MemoryExtractionService.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/write/MemoryLifecycleManagerTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManagerTest.java`

- [ ] **Step 1: Write the failing extraction orchestration and regression tests**

```java
@Test
void onTurnCompletedShouldPrependPersistedFixedMessagesForExtraction() {
    SuperAgentContext runtimeContext = new SuperAgentContext();
    runtimeContext.setSessionId("session-1");
    runtimeContext.setUserId("user-1");
    runtimeContext.setAgentKey("agent-1");
    runtimeContext.setTurnNo(3);
    runtimeContext.setLastActiveTime(LocalDateTime.of(2026, 3, 17, 10, 30));
    runtimeContext.setFixedMessages(new ArrayList<>(List.of(
            new SystemMessage("stage-system-prompt"),
            new UserMessage("Current user id: user-1"))));

    List<Message> rawMessages = List.of(
            new UserMessage("user-1"),
            new AssistantMessage("assistant-1"),
            new UserMessage("user-2"));
    List<Message> extractionMessages = List.of(
            new SystemMessage("stage-system-prompt"),
            new UserMessage("Current user id: user-1"),
            new UserMessage("user-1"),
            new AssistantMessage("assistant-1"),
            new UserMessage("user-2"));
    MemoryItem executionCandidate = new MemoryItem();
    executionCandidate.setId("execution");
    MemoryItem experienceCandidate = new MemoryItem();
    experienceCandidate.setId("experience");
    MemoryItem profileCandidate = new MemoryItem();
    profileCandidate.setId("profile");

    when(sessionContextStore.load("session-1")).thenReturn(Optional.of(runtimeContext));
    when(sessionContextStore.loadAllRawDialogueMessages("session-1")).thenReturn(rawMessages);
    when(memoryExtractionService.extractExecutionHistoryCandidates(runtimeContext, extractionMessages))
            .thenReturn(List.of(executionCandidate));
    when(memoryExtractionService.extractExperienceCandidates(runtimeContext, extractionMessages))
            .thenReturn(List.of(experienceCandidate));
    when(memoryExtractionService.extractProfileCandidates(runtimeContext, extractionMessages))
            .thenReturn(List.of(profileCandidate));

    memoryLifecycleManager.onTurnCompleted(runtimeContext);

    verify(memoryExtractionService).extractExecutionHistoryCandidates(runtimeContext, extractionMessages);
    verify(memoryExtractionService).extractExperienceCandidates(runtimeContext, extractionMessages);
    verify(memoryExtractionService).extractProfileCandidates(runtimeContext, extractionMessages);
}
```

```java
@Test
void preFlushBeforeCompactionShouldPrependRuntimeFixedMessages() {
    SuperAgentContext context = new SuperAgentContext();
    context.setFixedMessages(new ArrayList<>(List.of(
            new SystemMessage("stage-system-prompt"),
            new UserMessage("Current user id: user-1"))));
    List<Message> oldDialogueMessages = List.of(
            new UserMessage("older-user"),
            new AssistantMessage("older-assistant"));
    List<Message> extractionMessages = List.of(
            new SystemMessage("stage-system-prompt"),
            new UserMessage("Current user id: user-1"),
            new UserMessage("older-user"),
            new AssistantMessage("older-assistant"));

    when(memoryExtractionService.extractCompactionCandidates(context, extractionMessages))
            .thenReturn(List.of(new MemoryItem()));

    memoryLifecycleManager.preFlushBeforeCompaction(context, oldDialogueMessages);

    verify(memoryExtractionService).extractCompactionCandidates(context, extractionMessages);
}
```

```java
@Test
void buildModelMessagesShouldKeepFixedSummaryDialogueOrder() {
    SuperAgentContext context = new SuperAgentContext();
    context.setFixedMessages(new ArrayList<>(List.of(
            new SystemMessage("fixed-1"),
            new UserMessage("recall-1"))));
    context.setLatestCompressedMessage(new SystemMessage("summary-1"));
    context.setDialogueMessages(new ArrayList<>(List.of(
            new UserMessage("user-1"),
            new AssistantMessage("assistant-1"))));

    List<Message> messages = conversationMemoryManager.buildModelMessages(context);

    assertEquals(List.of("fixed-1", "recall-1", "summary-1", "user-1", "assistant-1"),
            messages.stream().map(Message::getText).toList());
}
```

- [ ] **Step 2: Run the targeted tests to confirm they fail**

Run:

```bash
mvn -q -f apex-agent/pom.xml -Dtest=MemoryLifecycleManagerTest,DefaultConversationMemoryManagerTest test
```

Expected:

- `MemoryLifecycleManagerTest` fails with Mockito verification errors because only raw dialogue messages are forwarded today
- the new regression test in `DefaultConversationMemoryManagerTest` compiles and documents the unchanged message order contract

- [ ] **Step 3: Implement shared extraction message assembly**

```java
public void preFlushBeforeCompaction(SuperAgentContext context, List<Message> oldDialogueMessages) {
    List<Message> extractionMessages = assembleExtractionMessages(context.getFixedMessages(), oldDialogueMessages);
    memoryWriteService.persistCandidates(
            memoryExtractionService.extractCompactionCandidates(context, extractionMessages));
}
```

```java
private void executeExecutionHistoryTask(String sessionId, Integer turnNo) {
    sessionContextStore.load(sessionId).ifPresent(context -> {
        if (context.getTurnNo() != null && turnNo != null && context.getTurnNo() < turnNo) {
            return;
        }
        List<Message> rawDialogueMessages = sessionContextStore.loadAllRawDialogueMessages(sessionId);
        List<Message> extractionMessages = assembleExtractionMessages(context.getFixedMessages(), rawDialogueMessages);
        memoryWriteService.persistCandidates(
                memoryExtractionService.extractExecutionHistoryCandidates(context, extractionMessages));
        memoryWriteService.persistCandidates(
                memoryExtractionService.extractExperienceCandidates(context, extractionMessages));
    });
}
```

```java
private void executeLongTermTask(String sessionId, LocalDateTime expectedLastActiveTime) {
    sessionContextStore.load(sessionId).ifPresent(context -> {
        if (context.getLastActiveTime() != null
                && expectedLastActiveTime != null
                && context.getLastActiveTime().isAfter(expectedLastActiveTime)) {
            return;
        }
        List<Message> rawDialogueMessages = sessionContextStore.loadAllRawDialogueMessages(sessionId);
        List<Message> extractionMessages = assembleExtractionMessages(context.getFixedMessages(), rawDialogueMessages);
        memoryWriteService.persistCandidates(
                memoryExtractionService.extractProfileCandidates(context, extractionMessages));
        longTermTasks.remove(sessionId);
    });
}
```

```java
private List<Message> assembleExtractionMessages(List<Message> fixedMessages, List<Message> dialogueMessages) {
    List<Message> messages = new ArrayList<>();
    if (fixedMessages != null) {
        messages.addAll(fixedMessages);
    }
    if (dialogueMessages != null) {
        messages.addAll(dialogueMessages);
    }
    return messages;
}
```

```java
public List<MemoryItem> extractExecutionHistoryCandidates(SuperAgentContext context, List<Message> messagesForExtraction) {
    if (!memoryConfigService.isExecutionHistoryEnabled()) {
        return List.of();
    }
    List<MemoryItem> items = invokePromptExtraction(
            context,
            messagesForExtraction,
            MemoryType.EXECUTION_HISTORY,
            memoryConfigService.getProperties().getExtraction().getExecutionHistoryPrompt());
    for (MemoryItem item : items) {
        if (item.getTimeScope() == null) {
            item.setTimeScope(ExecutionTimeScope.RECENT);
        }
    }
    return items;
}
```

- [ ] **Step 4: Re-run the targeted tests and verify they pass**

Run:

```bash
mvn -q -f apex-agent/pom.xml -Dtest=MemoryLifecycleManagerTest,DefaultConversationMemoryManagerTest test
```

Expected:

- `BUILD SUCCESS`
- `MemoryLifecycleManagerTest` proves execution history, experience, profile, and compaction pre-flush all receive `fixedMessages + dialogueMessages`
- `DefaultConversationMemoryManagerTest` proves model message ordering remains `fixed -> summary -> dialogue`

- [ ] **Step 5: Run the focused regression suite**

Run:

```bash
mvn -q -f apex-agent/pom.xml -Dtest=SessionContextEntityConverterTest,InMemorySessionContextStoreTest,MemoryLifecycleManagerTest,DefaultConversationMemoryManagerTest test
```

Expected:

- `BUILD SUCCESS`
- no regression in session round-trip, extraction orchestration, or model message ordering

- [ ] **Step 6: Commit the extraction assembly slice**

```bash
git add apex-agent/src/main/java/org/gemo/apex/memory/write/MemoryLifecycleManager.java apex-agent/src/main/java/org/gemo/apex/memory/extract/MemoryExtractionService.java apex-agent/src/test/java/org/gemo/apex/memory/write/MemoryLifecycleManagerTest.java apex-agent/src/test/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManagerTest.java
git commit -m "feat: include fixed message snapshots in memory extraction"
```
