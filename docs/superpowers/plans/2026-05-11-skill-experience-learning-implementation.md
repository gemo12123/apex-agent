# Skill Experience Learning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add agent-scoped Skill experience learning that records successful `activate_skill` usage, regenerates the latest experience on a daily threshold-based batch, and appends advisory experience to later `activate_skill` responses.

**Architecture:** Build a dedicated `org.gemo.apex.skills.learning` module with its own properties, repositories, hooks, collector, prompt service, extractor, and scheduler. Reuse the existing post-tool hook runtime, prompt template loader, session dialogue persistence, and Spring task scheduling patterns, but keep Skill learning state out of the generic memory type hierarchy.

**Tech Stack:** Java 25, Spring Boot 3.4, Spring AI, MyBatis Plus, PostgreSQL schema SQL, JUnit 5, Mockito

---

## File Structure

**Skill learning configuration and persistence**

- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningProperties.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningConfiguration.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillUsageRecordEntity.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillExperienceMemoryEntity.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillUsageRecordMapper.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillExperienceMemoryMapper.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageRecordRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceMemoryRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/InMemorySkillUsageRecordRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/InMemorySkillExperienceMemoryRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/JdbcSkillUsageRecordRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/JdbcSkillExperienceMemoryRepository.java`
- Modify: `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
- Modify: `apex-agent/src/main/resources/application.yml`

**Session message access and extraction pipeline**

- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillUsageRecord.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillExperienceMemory.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillSessionMessage.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillConversationSlice.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillUsageValidationResult.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageMessageCollector.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperiencePromptService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceExtractor.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/session/SessionContextStore.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/session/InMemorySessionContextStore.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/session/JdbcSessionContextStore.java`

**Activation-time hooks and runtime hardening**

- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceAugmentHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageRecorderHook.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java`

**Scheduled batch processing and prompt assets**

- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceScheduler.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageBatchService.java`
- Create: `apex-agent/src/main/resources/prompts/skills/skill-experience-learning.st`
- Modify: `apex-agent/src/main/java/org/gemo/apex/ApexApplication.java`

**Tests**

- Create: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillLearningRepositoryTest.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageMessageCollectorTest.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillExperienceExtractorTest.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageBatchServiceTest.java`
- Create: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillExperienceSchedulerTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/tool/skills/SkillsTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`

### Task 1: Add Skill Learning Properties and Persistence Foundations

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningProperties.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningConfiguration.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillUsageRecord.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillExperienceMemory.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillUsageRecordEntity.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillExperienceMemoryEntity.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillUsageRecordMapper.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillExperienceMemoryMapper.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageRecordRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceMemoryRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/InMemorySkillUsageRecordRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/InMemorySkillExperienceMemoryRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/JdbcSkillUsageRecordRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/JdbcSkillExperienceMemoryRepository.java`
- Modify: `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
- Modify: `apex-agent/src/main/resources/application.yml`
- Test: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillLearningRepositoryTest.java`

- [ ] **Step 1: Write the failing repository and property tests**

```java
@Test
void inMemoryUsageRepositoryShouldKeepDuplicatesAndGroupCounts() {
    InMemorySkillUsageRecordRepository repository = new InMemorySkillUsageRecordRepository();

    repository.insert(SkillUsageRecord.builder()
            .id("u1")
            .agentKey("default_agent")
            .skillName("writing-plans")
            .sessionId("session-1")
            .turnNo(3)
            .activationMessageSortNo(11L)
            .build());
    repository.insert(SkillUsageRecord.builder()
            .id("u2")
            .agentKey("default_agent")
            .skillName("writing-plans")
            .sessionId("session-1")
            .turnNo(4)
            .activationMessageSortNo(21L)
            .build());

    assertEquals(2, repository.countByAgentAndSkill().get("default_agent::writing-plans"));
    assertEquals(2, repository.findByAgentAndSkill("default_agent", "writing-plans").size());
}

@Test
void inMemoryExperienceRepositoryShouldOverwriteContentAndIncrementVersion() {
    InMemorySkillExperienceMemoryRepository repository = new InMemorySkillExperienceMemoryRepository();

    SkillExperienceMemory first = repository.upsert("default_agent", "writing-plans", "old");
    SkillExperienceMemory second = repository.upsert("default_agent", "writing-plans", "new");

    assertEquals(1L, first.getVersionNo());
    assertEquals(2L, second.getVersionNo());
    assertEquals("new", repository.find("default_agent", "writing-plans").orElseThrow().getContent());
}

@Test
void propertiesShouldExposeSpecDefaults() {
    SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();

    assertTrue(properties.isEnabled());
    assertEquals(5, properties.getUsageThreshold());
    assertEquals("0 0 4 * * *", properties.getDailyCron());
    assertEquals("Skill经验", properties.getExperienceSectionTitle());
}
```

- [ ] **Step 2: Run the repository test and verify it fails**

Run: `mvn -q "-Dtest=SkillLearningRepositoryTest" test`

Expected: FAIL with missing `SkillUsageRecord`, missing repositories, and missing `SkillExperienceLearningProperties`.

- [ ] **Step 3: Add the properties model and repository contracts**

```java
@Data
@ConfigurationProperties(prefix = "apex.skills.learning")
public class SkillExperienceLearningProperties {

    private boolean enabled = true;
    private int usageThreshold = 5;
    private String dailyCron = "0 0 4 * * *";
    private int longSessionMessageThreshold = 40;
    private int activationWindowBefore = 8;
    private int activationWindowAfter = 12;
    private String experiencePrompt = "classpath:prompts/skills/skill-experience-learning.st";
    private String experienceSectionTitle = "Skill经验";
}
```

```java
@Data
@Builder
public class SkillUsageRecord {
    private String id;
    private String agentKey;
    private String skillName;
    private String sessionId;
    private Integer turnNo;
    private Long activationMessageSortNo;
    private LocalDateTime createdTime;
}
```

```java
public interface SkillUsageRecordRepository {
    void insert(SkillUsageRecord record);
    Map<String, Integer> countByAgentAndSkill();
    List<SkillUsageRecord> findByAgentAndSkill(String agentKey, String skillName);
    void deleteByIds(Collection<String> ids);
}
```

```java
public interface SkillExperienceMemoryRepository {
    Optional<SkillExperienceMemory> find(String agentKey, String skillName);
    SkillExperienceMemory upsert(String agentKey, String skillName, String content);
}
```

- [ ] **Step 4: Add in-memory and JDBC repository implementations plus schema**

```java
@Repository
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySkillUsageRecordRepository implements SkillUsageRecordRepository {

    private final Map<String, SkillUsageRecord> storage = new ConcurrentHashMap<>();

    @Override
    public void insert(SkillUsageRecord record) {
        storage.put(record.getId(), record);
    }

    @Override
    public Map<String, Integer> countByAgentAndSkill() {
        return storage.values().stream()
                .collect(Collectors.toMap(
                        record -> record.getAgentKey() + "::" + record.getSkillName(),
                        record -> 1,
                        Integer::sum,
                        LinkedHashMap::new));
    }
}
```

```java
@Data
@TableName("skill_usage_record")
public class SkillUsageRecordEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("agent_key")
    private String agentKey;

    @TableField("skill_name")
    private String skillName;

    @TableField("session_id")
    private String sessionId;

    @TableField("turn_no")
    private Integer turnNo;

    @TableField("activation_message_sort_no")
    private Long activationMessageSortNo;

    @TableField("created_time")
    private LocalDateTime createdTime;
}
```

```sql
CREATE TABLE IF NOT EXISTS skill_usage_record (
    id VARCHAR(64) PRIMARY KEY,
    agent_key VARCHAR(64) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    turn_no INT,
    activation_message_sort_no BIGINT NOT NULL,
    created_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_skill_usage_record_agent_skill
    ON skill_usage_record(agent_key, skill_name);

CREATE TABLE IF NOT EXISTS skill_experience_memory (
    id VARCHAR(64) PRIMARY KEY,
    agent_key VARCHAR(64) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    content TEXT,
    version_no BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_experience_memory_agent_skill
    ON skill_experience_memory(agent_key, skill_name);
```

- [ ] **Step 5: Wire the configuration tree into `application.yml`**

```yaml
apex:
  skills:
    learning:
      enabled: true
      usage-threshold: 5
      daily-cron: "0 0 4 * * *"
      long-session-message-threshold: 40
      activation-window-before: 8
      activation-window-after: 12
      experience-prompt: classpath:prompts/skills/skill-experience-learning.st
      experience-section-title: Skill经验
```

```java
@Configuration
@EnableConfigurationProperties(SkillExperienceLearningProperties.class)
public class SkillExperienceLearningConfiguration {
}
```

- [ ] **Step 6: Re-run the repository test and verify it passes**

Run: `mvn -q "-Dtest=SkillLearningRepositoryTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/skills/learning apex-agent/src/main/resources/db/memory-schema-postgresql.sql apex-agent/src/main/resources/application.yml apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillLearningRepositoryTest.java
git commit -m "feat: add skill learning persistence foundations"
```

### Task 2: Extend Session Message Access and Build the Extraction Pipeline

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillSessionMessage.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillConversationSlice.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/model/SkillUsageValidationResult.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageMessageCollector.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperiencePromptService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceExtractor.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/session/SessionContextStore.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/session/InMemorySessionContextStore.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/session/JdbcSessionContextStore.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageMessageCollectorTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillExperienceExtractorTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`

- [ ] **Step 1: Write the failing collector and extractor tests**

```java
@Test
void collectShouldUseFullSessionForShortSessions() {
    when(sessionContextStore.loadSkillSessionMessages("session-1")).thenReturn(List.of(
            message(9L, "USER", null, null, "Need a plan"),
            message(10L, "ASSISTANT", "activate_skill", "{\"command\":\"writing-plans\"}", ""),
            message(11L, "TOOL", "activate_skill", null, "<activated_skill name=\"writing-plans\">...</activated_skill>"),
            message(12L, "ASSISTANT", null, null, "Plan incoming")));

    List<SkillConversationSlice> slices = collector.collectValidSlices(List.of(
            SkillUsageRecord.builder()
                    .id("u1")
                    .agentKey("default_agent")
                    .skillName("writing-plans")
                    .sessionId("session-1")
                    .activationMessageSortNo(10L)
                    .build()));

    assertEquals(1, slices.size());
    assertEquals(List.of(9L, 10L, 11L, 12L),
            slices.getFirst().getMessages().stream().map(SkillSessionMessage::getSortNo).toList());
}

@Test
void collectShouldDeleteInvalidRowsAndWindowLongSessions() {
    when(sessionContextStore.loadSkillSessionMessages("session-2")).thenReturn(List.of(
            message(20L, "USER", null, null, "hi"),
            message(21L, "ASSISTANT", "activate_skill", "{\"command\":\"wrong-skill\"}", ""),
            message(22L, "TOOL", "activate_skill", null, "bad")));

    SkillUsageValidationResult result = collector.validate(SkillUsageRecord.builder()
            .id("u2")
            .agentKey("default_agent")
            .skillName("writing-plans")
            .sessionId("session-2")
            .activationMessageSortNo(21L)
            .build());

    assertFalse(result.isValid());
    assertTrue(result.reason().contains("skill_name mismatch"));
}
```

```java
@Test
void buildPromptShouldIncludeExistingExperienceAndConversationSlices() {
    SkillExperiencePromptService service = new SkillExperiencePromptService(properties, promptTemplateLoader);
    when(promptTemplateLoader.load("classpath:prompts/skills/skill-experience-learning.st"))
            .thenReturn("agent={agentKey}\nskill={skillName}\nold={existingExperience}\nconv={conversationSlices}");

    String prompt = service.buildPrompt("default_agent", "writing-plans", "old-exp", List.of(
            new SkillConversationSlice("session-1", 10L, List.of(
                    new SkillSessionMessage(9L, "USER", null, null, "Need a plan")))));

    assertTrue(prompt.contains("agent=default_agent"));
    assertTrue(prompt.contains("skill=writing-plans"));
    assertTrue(prompt.contains("old=old-exp"));
    assertTrue(prompt.contains("session-1"));
}
```

- [ ] **Step 2: Run the collector-focused tests and verify they fail**

Run: `mvn -q "-Dtest=SkillUsageMessageCollectorTest,SkillExperienceExtractorTest,InMemorySessionContextStoreTest" test`

Expected: FAIL with missing `loadSkillSessionMessages`, missing collector classes, and missing prompt service.

- [ ] **Step 3: Add a message projection API to `SessionContextStore` and its implementations**

```java
public interface SessionContextStore {
    Optional<SuperAgentContext> load(String sessionId);
    void save(SuperAgentContext context);
    void appendDialogueMessages(String sessionId, Integer turnNo, Long baseSortNo, List<Message> messages);
    List<Message> loadAllRawDialogueMessages(String sessionId);
    List<SkillSessionMessage> loadSkillSessionMessages(String sessionId);
    int countUncompactedMessagesBeforeTurn(String sessionId, Integer turnNo);
    void compactDialogue(String sessionId, Message summaryMessage, Long compactedToSortNo, Integer turnNo);
    void delete(String sessionId);
}
```

```java
public List<SkillSessionMessage> loadSkillSessionMessages(String sessionId) {
    return dialogueMessageMapper.selectList(new LambdaQueryWrapper<AgentSessionDialogueMessageEntity>()
            .eq(AgentSessionDialogueMessageEntity::getSessionId, sessionId)
            .orderByAsc(AgentSessionDialogueMessageEntity::getSortNo)).stream()
            .map(entity -> new SkillSessionMessage(
                    entity.getSortNo(),
                    entity.getRole(),
                    entity.getToolName(),
                    entity.getMessagePayload(),
                    entity.getContent()))
            .toList();
}
```

- [ ] **Step 4: Implement validation, short-session full loading, and long-session windowing**

```java
public SkillUsageValidationResult validate(SkillUsageRecord record) {
    List<SkillSessionMessage> messages = sessionContextStore.loadSkillSessionMessages(record.getSessionId());
    return messages.stream()
            .filter(message -> Objects.equals(message.getSortNo(), record.getActivationMessageSortNo()))
            .findFirst()
            .map(message -> validateActivationMessage(record, message))
            .orElseGet(() -> SkillUsageValidationResult.invalid(record, "activation message not found"));
}

public List<SkillConversationSlice> collectValidSlices(List<SkillUsageRecord> records) {
    List<SkillConversationSlice> slices = new ArrayList<>();
    for (SkillUsageRecord record : records) {
        SkillUsageValidationResult validation = validate(record);
        if (!validation.isValid()) {
            continue;
        }
        List<SkillSessionMessage> allMessages = sessionContextStore.loadSkillSessionMessages(record.getSessionId());
        List<SkillSessionMessage> sliceMessages = allMessages.size() <= properties.getLongSessionMessageThreshold()
                ? allMessages
                : windowAround(allMessages, record.getActivationMessageSortNo());
        slices.add(new SkillConversationSlice(record.getSessionId(), record.getActivationMessageSortNo(), sliceMessages));
    }
    return slices;
}
```

- [ ] **Step 5: Implement prompt rendering and regenerative extraction**

```java
public class SkillExperiencePromptService {

    public String buildPrompt(String agentKey, String skillName, String existingExperience,
            List<SkillConversationSlice> slices) {
        String template = promptTemplateLoader.load(properties.getExperiencePrompt());
        return template
                .replace("{agentKey}", safe(agentKey))
                .replace("{skillName}", safe(skillName))
                .replace("{existingExperience}", safe(existingExperience))
                .replace("{conversationSlices}", renderSlices(slices));
    }
}
```

```java
public String regenerate(String agentKey, String skillName, String existingExperience,
        List<SkillConversationSlice> slices) {
    if (slices.isEmpty()) {
        return existingExperience;
    }
    String prompt = promptService.buildPrompt(agentKey, skillName, existingExperience, slices);
    return Optional.ofNullable(chatClient.prompt(prompt).call().content())
            .map(String::trim)
            .filter(content -> !content.isBlank())
            .orElse(existingExperience);
}
```

- [ ] **Step 6: Re-run the collector-focused tests and verify they pass**

Run: `mvn -q "-Dtest=SkillUsageMessageCollectorTest,SkillExperienceExtractorTest,InMemorySessionContextStoreTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/skills/learning apex-agent/src/main/java/org/gemo/apex/memory/session/SessionContextStore.java apex-agent/src/main/java/org/gemo/apex/memory/session/InMemorySessionContextStore.java apex-agent/src/main/java/org/gemo/apex/memory/session/JdbcSessionContextStore.java apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageMessageCollectorTest.java apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillExperienceExtractorTest.java apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
git commit -m "feat: add skill usage message collection pipeline"
```

### Task 3: Harden Post-Hook Isolation and Add Skill Activation Hooks

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceAugmentHook.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageRecorderHook.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java`
- Modify: `apex-agent/src/main/resources/application.yml`
- Test: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/tool/skills/SkillsTest.java`

- [ ] **Step 1: Write the failing hook-isolation and augmentation tests**

```java
@Test
void runPostHooksShouldKeepOriginalResultWhenAugmentHookThrows() {
    AgentHooksConfig config = AgentHooksConfig.builder()
            .postToolCall(List.of(HookBindingConfig.builder()
                    .bean("skillExperienceAugmentHook")
                    .tools(List.of("activate_skill"))
                    .order(10)
                    .build()))
            .build();

    when(agentWorkspaceService.getHooks("default_agent")).thenReturn(config);
    when(applicationContext.getBean("skillExperienceAugmentHook", PostToolCallHook.class))
            .thenReturn(context -> {
                throw new IllegalStateException("db down");
            });

    PostToolCallHookResult result = runtime.runPostHooks(PostToolCallHookContext.builder()
            .agentKey("default_agent")
            .sessionId("session-1")
            .toolCallId("call-1")
            .invocationId("invoke-1")
            .toolName("activate_skill")
            .arguments(Map.of("command", "writing-plans"))
            .currentResult("<activated_skill name=\"writing-plans\"><instructions>body</instructions></activated_skill>")
            .originalResult("<activated_skill name=\"writing-plans\"><instructions>body</instructions></activated_skill>")
            .build());

    assertEquals(PostToolCallHookResult.Outcome.KEEP, result.getOutcome());
}
```

```java
@Test
void executeToolCallsShouldAppendExperienceAndRecordUsageWithoutBlockingToolResult() {
    when(hookRuntime.runPostHooks(any())).thenReturn(PostToolCallHookResult.replaceResult("""
            <activated_skill name="writing-plans">
              <instructions>
                body

                # Skill经验
                以下经验来自该 Skill 在当前 Agent 下的历史使用总结，仅供参考。
              </instructions>
            </activated_skill>
            """));

    ToolExecutionResult result = manager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(assistantMessage))));

    ToolResponseMessage response = (ToolResponseMessage) result.conversationHistory().get(2);
    assertTrue(response.getResponses().getFirst().responseData().contains("# Skill经验"));
}
```

- [ ] **Step 2: Run the hook-focused tests and verify they fail**

Run: `mvn -q "-Dtest=DefaultAgentHookRuntimeTest,CustomToolCallingManagerTest,SkillsTest" test`

Expected: FAIL because `DefaultAgentHookRuntime` still propagates exceptions and there are no skill learning hooks.

- [ ] **Step 3: Catch post-hook failures in `DefaultAgentHookRuntime` and log tracing identifiers**

```java
for (HookBindingConfig binding : matchingBindings(hooks.getPostToolCall(), context.getToolName())) {
    try {
        PostToolCallHook hook = applicationContext.getBean(binding.getBean(), PostToolCallHook.class);
        PostToolCallHookResult result = hook.apply(context.toBuilder()
                .hookOptions(binding.getOptions())
                .hookSource(binding.getBean())
                .currentResult(currentResult)
                .build());
        if (result.getOutcome() == PostToolCallHookResult.Outcome.REPLACE_RESULT) {
            currentResult = result.getNextResult();
            replaced = true;
        }
    } catch (Exception ex) {
        logger.warn("Post hook failed, agentKey={}, sessionId={}, toolCallId={}, invocationId={}, skillName={}, hookBean={}",
                context.getAgentKey(),
                context.getSessionId(),
                context.getToolCallId(),
                context.getInvocationId(),
                context.getArguments() != null ? context.getArguments().get("command") : null,
                binding.getBean(),
                ex);
    }
}
```

- [ ] **Step 4: Add both `activate_skill` hooks and compute activation sort from the already-persisted assistant tool-call message**

```java
@Component("skillUsageRecorderHook")
public class SkillUsageRecorderHook implements PostToolCallHook {

    @Override
    public PostToolCallHookResult apply(PostToolCallHookContext context) {
        try {
            if (!ToolNames.ACTIVATE_SKILL.equals(context.getToolName())) {
                return PostToolCallHookResult.keep();
            }
            SuperAgentContext superAgentContext = context.getSuperAgentContext();
            if (superAgentContext == null || context.getArguments() == null || context.getArguments().get("command") == null) {
                return PostToolCallHookResult.keep();
            }
            usageRepository.insert(SkillUsageRecord.builder()
                    .id(IdUtil.simpleUUID())
                    .agentKey(context.getAgentKey())
                    .skillName(String.valueOf(context.getArguments().get("command")))
                    .sessionId(context.getSessionId())
                    .turnNo(superAgentContext.getTurnNo())
                    .activationMessageSortNo(Math.max(1L, superAgentContext.getNextMessageSortNo() - 1L))
                    .createdTime(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to record skill usage, agentKey={}, sessionId={}, toolCallId={}, invocationId={}",
                    context.getAgentKey(), context.getSessionId(), context.getToolCallId(), context.getInvocationId(), ex);
        }
        return PostToolCallHookResult.keep();
    }
}
```

```java
@Component("skillExperienceAugmentHook")
public class SkillExperienceAugmentHook implements PostToolCallHook {

    @Override
    public PostToolCallHookResult apply(PostToolCallHookContext context) {
        try {
            if (!ToolNames.ACTIVATE_SKILL.equals(context.getToolName())) {
                return PostToolCallHookResult.keep();
            }
            String skillName = String.valueOf(context.getArguments().get("command"));
            Optional<SkillExperienceMemory> experience = experienceRepository.find(context.getAgentKey(), skillName);
            if (experience.isEmpty()) {
                return PostToolCallHookResult.keep();
            }
            String section = """
                    # %s

                    以下经验来自该 Skill 在当前 Agent 下的历史使用总结，仅供参考。
                    请结合当前任务判断，不要完全依赖这些经验；当经验与当前上下文冲突时，以当前任务事实为准。

                    %s
                    """.formatted(properties.getExperienceSectionTitle(), experience.get().getContent());
            String replaced = context.getCurrentResult().replace("</instructions>", "\n\n" + section + "\n</instructions>");
            return PostToolCallHookResult.replaceResult(replaced);
        } catch (Exception ex) {
            log.warn("Failed to augment skill result, agentKey={}, sessionId={}, toolCallId={}, invocationId={}",
                    context.getAgentKey(), context.getSessionId(), context.getToolCallId(), context.getInvocationId(), ex);
            return PostToolCallHookResult.keep();
        }
    }
}
```

- [ ] **Step 5: Bind the hooks to shipped agents in `application.yml`**

```yaml
apex:
  global:
    agents:
      default_agent:
        hooks:
          post-tool-call:
            - bean: skillExperienceAugmentHook
              tools: ["activate_skill"]
              order: 10
            - bean: skillUsageRecorderHook
              tools: ["activate_skill"]
              order: 20
      deer-flow:
        hooks:
          post-tool-call:
            - bean: skillExperienceAugmentHook
              tools: ["activate_skill"]
              order: 10
            - bean: skillUsageRecorderHook
              tools: ["activate_skill"]
              order: 20
      meeting_tool:
        hooks:
          post-tool-call:
            - bean: skillExperienceAugmentHook
              tools: ["activate_skill"]
              order: 10
            - bean: skillUsageRecorderHook
              tools: ["activate_skill"]
              order: 20
      contacts_tool:
        hooks:
          post-tool-call:
            - bean: skillExperienceAugmentHook
              tools: ["activate_skill"]
              order: 10
            - bean: skillUsageRecorderHook
              tools: ["activate_skill"]
              order: 20
```

- [ ] **Step 6: Re-run the hook-focused tests and verify they pass**

Run: `mvn -q "-Dtest=DefaultAgentHookRuntimeTest,CustomToolCallingManagerTest,SkillsTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/skills/learning apex-agent/src/main/java/org/gemo/apex/hook/DefaultAgentHookRuntime.java apex-agent/src/main/resources/application.yml apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java apex-agent/src/test/java/org/gemo/apex/tool/skills/SkillsTest.java
git commit -m "feat: add skill activation learning hooks"
```

### Task 4: Add Threshold Batch Processing and Daily Scheduling

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillUsageBatchService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/skills/learning/SkillExperienceScheduler.java`
- Create: `apex-agent/src/main/resources/prompts/skills/skill-experience-learning.st`
- Modify: `apex-agent/src/main/java/org/gemo/apex/ApexApplication.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageBatchServiceTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillExperienceSchedulerTest.java`

- [ ] **Step 1: Write the failing batch-processing tests**

```java
@Test
void processEligibleGroupShouldDeleteInvalidRowsBeforeThresholdCheck() {
    when(usageRepository.findByAgentAndSkill("default_agent", "writing-plans")).thenReturn(List.of(
            record("u1", 10L),
            record("u2", 20L),
            record("u3", 30L)));
    when(messageCollector.validate(record("u1", 10L))).thenReturn(SkillUsageValidationResult.valid(record("u1", 10L)));
    when(messageCollector.validate(record("u2", 20L))).thenReturn(SkillUsageValidationResult.invalid(record("u2", 20L), "bad"));
    when(messageCollector.validate(record("u3", 30L))).thenReturn(SkillUsageValidationResult.valid(record("u3", 30L)));

    service.processGroup("default_agent", "writing-plans", 3);

    verify(usageRepository).deleteByIds(List.of("u2"));
    verifyNoInteractions(extractor);
}

@Test
void processEligibleGroupShouldUpsertExperienceAndDeleteParticipatingRows() {
    List<SkillUsageRecord> validRecords = List.of(record("u1", 10L), record("u2", 20L));
    when(usageRepository.findByAgentAndSkill("default_agent", "writing-plans")).thenReturn(validRecords);
    when(messageCollector.validate(any())).thenAnswer(invocation -> SkillUsageValidationResult.valid(invocation.getArgument(0)));
    when(messageCollector.collectValidSlices(validRecords)).thenReturn(List.of(slice("session-1", 10L), slice("session-2", 20L)));
    when(experienceRepository.find("default_agent", "writing-plans")).thenReturn(Optional.of(
            SkillExperienceMemory.builder().agentKey("default_agent").skillName("writing-plans").content("old").versionNo(1L).build()));
    when(extractor.regenerate("default_agent", "writing-plans", "old", List.of(slice("session-1", 10L), slice("session-2", 20L))))
            .thenReturn("new");

    service.processGroup("default_agent", "writing-plans", 2);

    verify(experienceRepository).upsert("default_agent", "writing-plans", "new");
    verify(usageRepository).deleteByIds(List.of("u1", "u2"));
}
```

```java
@Test
void schedulerShouldOnlyProcessGroupsMeetingThreshold() {
    when(usageRepository.countByAgentAndSkill()).thenReturn(Map.of(
            "default_agent::writing-plans", 5,
            "default_agent::brainstorming", 2));

    scheduler.runDailyScan();

    verify(batchService).processGroup("default_agent", "writing-plans", 5);
    verify(batchService, never()).processGroup("default_agent", "brainstorming", 2);
}
```

- [ ] **Step 2: Run the scheduler tests and verify they fail**

Run: `mvn -q "-Dtest=SkillUsageBatchServiceTest,SkillExperienceSchedulerTest" test`

Expected: FAIL with missing batch service and scheduler classes.

- [ ] **Step 3: Implement validation cleanup, threshold re-check, and regenerative overwrite**

```java
private final ConcurrentMap<String, ReentrantLock> groupLocks = new ConcurrentHashMap<>();

@Transactional
public void processGroup(String agentKey, String skillName, int groupedCount) {
    ReentrantLock lock = groupLocks.computeIfAbsent(agentKey + "::" + skillName, key -> new ReentrantLock());
    if (!lock.tryLock()) {
        return;
    }
    try {
        List<SkillUsageRecord> records = usageRepository.findByAgentAndSkill(agentKey, skillName);
        List<SkillUsageRecord> valid = new ArrayList<>();
        List<String> invalidIds = new ArrayList<>();

        for (SkillUsageRecord record : records) {
            SkillUsageValidationResult validation = messageCollector.validate(record);
            if (validation.isValid()) {
                valid.add(record);
            } else {
                invalidIds.add(record.getId());
            }
        }

        if (!invalidIds.isEmpty()) {
            usageRepository.deleteByIds(invalidIds);
        }
        if (valid.size() < properties.getUsageThreshold()) {
            return;
        }

        List<SkillConversationSlice> slices = messageCollector.collectValidSlices(valid);
        String existingExperience = experienceRepository.find(agentKey, skillName)
                .map(SkillExperienceMemory::getContent)
                .orElse("");
        String rewritten = extractor.regenerate(agentKey, skillName, existingExperience, slices);
        experienceRepository.upsert(agentKey, skillName, rewritten);
        usageRepository.deleteByIds(valid.stream().map(SkillUsageRecord::getId).toList());
    } finally {
        lock.unlock();
    }
}
```

- [ ] **Step 4: Implement the daily scheduler and enable scheduling**

```java
@Slf4j
@Component
public class SkillExperienceScheduler {

    @Scheduled(cron = "${apex.skills.learning.daily-cron:0 0 4 * * *}")
    public void runDailyScan() {
        if (!properties.isEnabled()) {
            return;
        }
        usageRepository.countByAgentAndSkill().forEach((groupKey, count) -> {
            if (count < properties.getUsageThreshold()) {
                return;
            }
            String[] parts = groupKey.split("::", 2);
            batchService.processGroup(parts[0], parts[1], count);
        });
    }
}
```

```java
@SpringBootApplication
@EnableScheduling
public class ApexApplication {
}
```

- [ ] **Step 5: Add the extraction prompt asset**

```text
你是一个 Skill 经验总结器。请基于当前 Agent 下同一 Skill 的历史使用证据，重写一份最新经验。

Agent: {agentKey}
Skill: {skillName}

已有经验：
{existingExperience}

历史对话片段：
{conversationSlices}

输出要求：
1. 直接输出 Markdown 正文，不要输出 JSON。
2. 只保留对后续执行有帮助的经验。
3. 如果历史经验与当前证据冲突，重写为更新后的结论。
4. 不要机械复述所有对话。
```

- [ ] **Step 6: Re-run the scheduler tests and verify they pass**

Run: `mvn -q "-Dtest=SkillUsageBatchServiceTest,SkillExperienceSchedulerTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add apex-agent/src/main/java/org/gemo/apex/skills/learning apex-agent/src/main/resources/prompts/skills/skill-experience-learning.st apex-agent/src/main/java/org/gemo/apex/ApexApplication.java apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageBatchServiceTest.java apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillExperienceSchedulerTest.java
git commit -m "feat: add scheduled skill experience regeneration"
```

### Task 5: Run End-to-End Verification and Close Spec Gaps

**Files:**
- Modify: `apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/tool/skills/SkillsTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageBatchServiceTest.java`
- Modify: `apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageMessageCollectorTest.java`

- [ ] **Step 1: Add the remaining acceptance tests**

```java
@Test
void augmentHookShouldPreserveActivatedSkillWrapper() {
    PostToolCallHookResult result = hook.apply(PostToolCallHookContext.builder()
            .agentKey("default_agent")
            .toolName("activate_skill")
            .arguments(Map.of("command", "writing-plans"))
            .currentResult("""
                    <activated_skill name="writing-plans">
                      <instructions>
                        body
                      </instructions>
                    </activated_skill>
                    """)
            .build());

    assertEquals(PostToolCallHookResult.Outcome.REPLACE_RESULT, result.getOutcome());
    assertTrue(result.getNextResult().contains("<activated_skill name=\"writing-plans\">"));
    assertTrue(result.getNextResult().contains("# Skill经验"));
}

@Test
void failedExtractionShouldKeepValidUsageRowsForRetry() {
    when(extractor.regenerate(any(), any(), any(), any())).thenThrow(new IllegalStateException("llm down"));

    assertThrows(IllegalStateException.class, () -> service.processGroup("default_agent", "writing-plans", 2));

    verify(usageRepository, never()).deleteByIds(List.of("u1", "u2"));
}
```

- [ ] **Step 2: Run the focused backend verification suite**

Run: `mvn -q "-Dtest=SkillLearningRepositoryTest,SkillUsageMessageCollectorTest,SkillExperienceExtractorTest,SkillUsageBatchServiceTest,SkillExperienceSchedulerTest,DefaultAgentHookRuntimeTest,CustomToolCallingManagerTest,SkillsTest,InMemorySessionContextStoreTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 3: Run the broader regression suite for touched backend seams**

Run: `mvn -q "-Dtest=ToolCallProcessorTest,MemoryLifecycleManagerTest,SuperAgentFactoryTest,ChatControllerTest" test`

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add apex-agent/src/test/java/org/gemo/apex/hook/DefaultAgentHookRuntimeTest.java apex-agent/src/test/java/org/gemo/apex/component/CustomToolCallingManagerTest.java apex-agent/src/test/java/org/gemo/apex/tool/skills/SkillsTest.java apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageBatchServiceTest.java apex-agent/src/test/java/org/gemo/apex/skills/learning/SkillUsageMessageCollectorTest.java
git commit -m "test: cover skill learning acceptance flow"
```
