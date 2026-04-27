# Postgres Session Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace automatic recent-memory prompt injection with PostgreSQL-backed durable search metadata and a single explicit `session_search` tool over persisted dialogue messages and summaries.

**Architecture:** Remove the old `MemoryRecallService` path from normal prompt assembly, keep JDBC as the durable storage toggle, and make PostgreSQL the supported search-capable implementation. Store `search_text` plus `pgvector` embeddings for dialogue messages, summaries, and execution-history memories; let PostgreSQL generate `tsvector`; expose historical retrieval only through a new built-in `session_search` tool backed by a dedicated hybrid search service.

**Tech Stack:** Spring Boot 3.4, Spring AI tool annotations, Spring AI `EmbeddingModel`, MyBatis Plus, `NamedParameterJdbcTemplate`, PostgreSQL `tsvector`, PostgreSQL `pgvector`, JUnit 5, Mockito

---

## File Structure

### Existing files to modify

- `apex-agent/pom.xml`
  - Restore the PostgreSQL JDBC driver dependency needed for the supported JDBC search path while leaving the MySQL dependency untouched.
- `apex-agent/src/main/resources/application.yml`
  - Add memory search defaults such as embedding dimension, text search config, and `session_search` limits.
- `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
  - Add `pgvector` extension setup, search columns, generated `tsvector` columns, and search indexes.
- `apex-agent/src/main/java/org/gemo/apex/constant/ToolNames.java`
  - Add the `SESSION_SEARCH` tool name constant.
- `apex-agent/src/main/java/org/gemo/apex/context/SuperAgentContext.java`
  - Remove the `MemoryRecallPackage` field and any related imports/comments.
- `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java`
  - Stop wiring `MemoryRecallService` into new-turn and resume flows.
- `apex-agent/src/main/java/org/gemo/apex/component/tool/BuiltInToolProvider.java`
  - Register the new `SessionSearchTool`.
- `apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryProperties.java`
  - Add nested search properties for text search config, embedding dimension, and tool result limits.
- `apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryConfiguration.java`
  - Remove `MemoryReadRepository` bean wiring and add beans for search/embedding components.
- `apex-agent/src/main/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManager.java`
  - Stop rendering/injecting recall text; keep only stage prompt and current user id fixed messages.
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueMessageEntity.java`
  - Add `searchText` and fields needed by the search-index refresh flow.
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueSummaryEntity.java`
  - Add `searchText`.
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/UserExecutionHistoryMemoryEntity.java`
  - Add `searchText`.
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java`
  - Populate `searchText` for persisted dialogue messages and summaries.
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/MemoryItemEntityConverter.java`
  - Populate `searchText` for execution-history entities.
- `apex-agent/src/main/java/org/gemo/apex/memory/session/JdbcSessionContextStore.java`
  - Refresh message/summary search metadata after inserts and summary upserts.
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryWriteRepository.java`
  - Refresh execution-history search metadata after upserts.
- `README.md`
  - Explain the Postgres-first JDBC requirement and schema prerequisites.
- `docs/快速开始.md`
  - Document the required PostgreSQL extension and JDBC search properties.

### Existing files to delete

- `apex-agent/src/main/java/org/gemo/apex/memory/model/MemoryRecallPackage.java`
- `apex-agent/src/main/java/org/gemo/apex/memory/recall/MemoryRecallService.java`
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/MemoryReadRepository.java`
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryReadRepository.java`
- `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryReadRepository.java`

### New files to create

- `apex-agent/src/main/java/org/gemo/apex/memory/search/SearchIndexTextBuilder.java`
  - Build normalized `search_text` for messages, summaries, and execution-history rows.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/MemoryEmbeddingService.java`
  - Small abstraction for synchronous embedding generation.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingService.java`
  - `EmbeddingModel`-backed implementation using `embed(String)`.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/PgVectorLiteralFormatter.java`
  - Convert `float[]` embeddings into PostgreSQL vector literal strings such as `[0.1,0.2]`.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/PostgresSearchIndexUpdater.java`
  - Transaction-friendly SQL updater that writes `search_text` and `embedding` for messages, summaries, and execution-history rows.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchQuery.java`
  - Tool/service input model.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchHit.java`
  - Search hit output model.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchResult.java`
  - Top-level tool result model.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchScope.java`
  - Runtime scope model carrying `userId` and `agentKey`.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchRepository.java`
  - Search repository contract.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/PostgresSessionSearchRepository.java`
  - `NamedParameterJdbcTemplate` hybrid query implementation joining `agent_session`.
- `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchService.java`
  - Generate query embeddings, merge FTS/vector hits, normalize scores, deduplicate, and trim.
- `apex-agent/src/main/java/org/gemo/apex/tool/SessionSearchTool.java`
  - Built-in tool entrypoint using `@Tool`.

### Existing tests to modify

- `apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java`
- `apex-agent/src/test/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManagerTest.java`
- `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`
- `apex-agent/src/test/java/org/gemo/apex/component/tool/BuiltInToolProviderTest.java`

### New tests to create

- `apex-agent/src/test/java/org/gemo/apex/memory/search/SearchIndexTextBuilderTest.java`
- `apex-agent/src/test/java/org/gemo/apex/memory/search/PgVectorLiteralFormatterTest.java`
- `apex-agent/src/test/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingServiceTest.java`
- `apex-agent/src/test/java/org/gemo/apex/memory/search/PostgresSearchIndexUpdaterTest.java`
- `apex-agent/src/test/java/org/gemo/apex/memory/search/SessionSearchServiceTest.java`
- `apex-agent/src/test/java/org/gemo/apex/tool/SessionSearchToolTest.java`

## Task 1: Remove Automatic Recall From Runtime Prompt Assembly

**Files:**
- Delete: `apex-agent/src/main/java/org/gemo/apex/memory/model/MemoryRecallPackage.java`
- Delete: `apex-agent/src/main/java/org/gemo/apex/memory/recall/MemoryRecallService.java`
- Delete: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/MemoryReadRepository.java`
- Delete: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryReadRepository.java`
- Delete: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryReadRepository.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/context/SuperAgentContext.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryConfiguration.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManager.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManagerTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java`

- [ ] **Step 1: Write the failing tests for the new no-recall behavior**

```java
// apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java
@Test
void createContextShouldNotPopulateLegacyRecallState() {
    when(sessionContextStore.load("session-1")).thenReturn(Optional.empty());

    SuperAgentContext context = superAgentFactory.createContext("session-1", "agent-1", "hello");

    assertEquals("hello", context.getDialogueMessages().getFirst().getText());
    assertEquals(1, context.getDialogueMessages().size());
}

// apex-agent/src/test/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManagerTest.java
@Test
void refreshFixedMessagesShouldOnlyContainStagePromptAndUserId() {
    SuperAgentContext context = new SuperAgentContext();
    context.setUserId("user-123");

    conversationMemoryManager.refreshFixedMessages(context, "stage-system-prompt");

    assertEquals(List.of("stage-system-prompt", "Current user id: user-123"),
            context.getFixedMessages().stream().map(Message::getText).toList());
}

// apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
@Test
void saveAndLoadShouldNotDependOnRemovedRecallPackage() {
    SuperAgentContext context = buildContext();
    store.save(context);

    SuperAgentContext loaded = store.load("session-1").orElseThrow();

    assertEquals("fixed-1", loaded.getFixedMessages().getFirst().getText());
}
```

- [ ] **Step 2: Run the focused tests to verify they fail on the old recall wiring**

Run:

```bash
mvn -q "-Dtest=SuperAgentFactoryTest,DefaultConversationMemoryManagerTest,InMemorySessionContextStoreTest" test
```

Expected:

- compilation or assertion failures mentioning `MemoryRecallPackage`, `MemoryRecallService`, or extra fixed messages

- [ ] **Step 3: Remove the recall field/service/repository path and simplify prompt assembly**

```java
// apex-agent/src/main/java/org/gemo/apex/context/SuperAgentContext.java
@JsonIgnore
private List<Message> fixedMessages = new ArrayList<>();

// remove the MemoryRecallPackage field and its import entirely

// apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java
private SuperAgentContext initializeNewTurn(SuperAgentContext context, String sessionId, String agentKey,
        String userId, String userQuery) {
    context.setSessionId(sessionId);
    context.setAgentKey(agentKey);
    context.setUserId(userId);
    context.setLastActiveTime(LocalDateTime.now());
    context.setPlan(null);
    context.setCurrentStageId(null);
    context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
    context.setPendingToolResult(null);
    context.setTurnNo(context.getTurnNo() != null ? context.getTurnNo() + 1 : 1);
    context.setTurnStartSortNo(context.getLatestCompressedSortNo());
    context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());

    prepareRuntimeContext(context, agentKey);
    applyDefaultStageConfig(context, agentKey);

    UserMessage userMessage = new UserMessage(userQuery);
    context.addMessage(userMessage);
    persistNewTurn(context, userMessage);
    return context;
}

public SuperAgentContext resumeContext(String sessionId, Map<String, Object> humanResponse) {
    SuperAgentContext context = sessionContextStore.load(sessionId).orElse(null);
    if (context == null || context.getExecutionStatus() != ExecutionStatus.HUMAN_IN_THE_LOOP) {
        return null;
    }
    prepareRuntimeContext(context, context.getAgentKey());
    context.setUserId(UserContextHolder.getUserId());
    context.setPendingToolResult(humanResponse != null && !humanResponse.isEmpty() ? humanResponse : new HashMap<>());
    context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
    return context;
}

// apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryConfiguration.java
// delete the entire MemoryReadRepository @Bean method so this configuration class
// only wires SessionContextStore, MemoryWriteRepository, and MemoryManageRepository

// apex-agent/src/main/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManager.java
@Override
public void refreshFixedMessages(SuperAgentContext context, String stageSystemPrompt) {
    List<Message> fixedMessages = new ArrayList<>();
    fixedMessages.add(new SystemMessage(stageSystemPrompt));
    if (context.getUserId() != null && !context.getUserId().isBlank()) {
        fixedMessages.add(new UserMessage("Current user id: " + context.getUserId()));
    }
    context.setFixedMessages(fixedMessages);
}
```

- [ ] **Step 4: Run the focused tests to verify the new prompt boundary passes**

Run:

```bash
mvn -q "-Dtest=SuperAgentFactoryTest,DefaultConversationMemoryManagerTest,InMemorySessionContextStoreTest" test
```

Expected:

- PASS
- no references to `MemoryRecallPackage` remain in compile output

- [ ] **Step 5: Commit the recall removal slice**

```bash
git add apex-agent/src/main/java/org/gemo/apex/context/SuperAgentContext.java apex-agent/src/main/java/org/gemo/apex/core/SuperAgentFactory.java apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryConfiguration.java apex-agent/src/main/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManager.java apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java apex-agent/src/test/java/org/gemo/apex/memory/conversation/DefaultConversationMemoryManagerTest.java apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
git rm apex-agent/src/main/java/org/gemo/apex/memory/model/MemoryRecallPackage.java apex-agent/src/main/java/org/gemo/apex/memory/recall/MemoryRecallService.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/MemoryReadRepository.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryReadRepository.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryReadRepository.java
git commit -m "refactor: remove automatic memory recall injection"
```

## Task 2: Add PostgreSQL Search Schema And Search Configuration

**Files:**
- Modify: `apex-agent/pom.xml`
- Modify: `apex-agent/src/main/resources/application.yml`
- Modify: `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryProperties.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueMessageEntity.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueSummaryEntity.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/UserExecutionHistoryMemoryEntity.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverterTest.java`

- [ ] **Step 1: Write the failing tests for search schema/config fields**

```java
// apex-agent/src/test/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverterTest.java
@Test
void toDialogueEntitiesShouldPopulateSearchTextFromMessageContent() {
    List<AgentSessionDialogueMessageEntity> entities = SessionContextEntityConverter.toDialogueEntities(
            "session-1", 3, 0L, List.of(new UserMessage("searchable text")));

    assertEquals("searchable text", entities.getFirst().getSearchText());
}

@Test
void toSummaryEntityShouldPopulateSearchTextFromSummaryContent() {
    AgentSessionDialogueSummaryEntity entity = SessionContextEntityConverter.toSummaryEntity(
            "session-1", new SystemMessage("summary text"), 4L, 3, LocalDateTime.now());

    assertEquals("summary text", entity.getSearchText());
}
```

- [ ] **Step 2: Run the converter test to verify the new fields are missing**

Run:

```bash
mvn -q "-Dtest=SessionContextEntityConverterTest" test
```

Expected:

- compile failures because `getSearchText()` does not exist yet

- [ ] **Step 3: Add PostgreSQL schema/search config and entity fields**

```xml
<!-- apex-agent/pom.xml -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.2</version>
    <scope>runtime</scope>
</dependency>
```

```yaml
# apex-agent/src/main/resources/application.yml
apex:
  memory:
    search:
      embedding-dimension: 1536
      text-search-config: simple
      default-session-search-limit: 8
      max-session-search-limit: 20
      min-embedding-text-length: 8
```

```sql
-- apex-agent/src/main/resources/db/memory-schema-postgresql.sql
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE agent_session_dialogue_message
    ADD COLUMN IF NOT EXISTS search_text TEXT,
    ADD COLUMN IF NOT EXISTS search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', COALESCE(search_text, ''))
    ) STORED,
    ADD COLUMN IF NOT EXISTS embedding vector(1536);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_message_search_vector
    ON agent_session_dialogue_message USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_message_embedding
    ON agent_session_dialogue_message USING hnsw (embedding vector_cosine_ops);
```

```java
// apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryProperties.java
private SearchProperties search = new SearchProperties();

@Data
public static class SearchProperties {
    private int embeddingDimension = 1536;
    private String textSearchConfig = "simple";
    private int defaultSessionSearchLimit = 8;
    private int maxSessionSearchLimit = 20;
    private int minEmbeddingTextLength = 8;
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueMessageEntity.java
@TableField("search_text")
private String searchText;

// apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueSummaryEntity.java
@TableField("search_text")
private String searchText;

// apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/UserExecutionHistoryMemoryEntity.java
@TableField("search_text")
private String searchText;
```

- [ ] **Step 4: Run the converter test again to verify the search fields compile and pass**

Run:

```bash
mvn -q "-Dtest=SessionContextEntityConverterTest" test
```

Expected:

- PASS

- [ ] **Step 5: Commit the schema/config foundation**

```bash
git add apex-agent/pom.xml apex-agent/src/main/resources/application.yml apex-agent/src/main/resources/db/memory-schema-postgresql.sql apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryProperties.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueMessageEntity.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/AgentSessionDialogueSummaryEntity.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/entity/UserExecutionHistoryMemoryEntity.java apex-agent/src/test/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverterTest.java
git commit -m "feat: add postgres search schema foundation"
```

## Task 3: Build Search Text And Embedding-Aware Write Paths

**Files:**
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SearchIndexTextBuilder.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/MemoryEmbeddingService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/PgVectorLiteralFormatter.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/PostgresSearchIndexUpdater.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryConfiguration.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/MemoryItemEntityConverter.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/session/JdbcSessionContextStore.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryWriteRepository.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/search/SearchIndexTextBuilderTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/search/PgVectorLiteralFormatterTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingServiceTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/search/PostgresSearchIndexUpdaterTest.java`

- [ ] **Step 1: Write failing unit tests for text building, vector formatting, and fallback behavior**

```java
// apex-agent/src/test/java/org/gemo/apex/memory/search/SearchIndexTextBuilderTest.java
@Test
void buildExecutionHistoryTextShouldIncludeTitleContentTopicAndStructuredPayload() {
    MemoryItem item = new MemoryItem();
    item.setTitle("Fix recall");
    item.setContent("Use session search");
    item.setTopicKey("memory");
    item.setStructuredPayload("{\"result\":\"ok\"}");

    String searchText = new SearchIndexTextBuilder().buildExecutionHistoryText(item);

    assertTrue(searchText.contains("Fix recall"));
    assertTrue(searchText.contains("Use session search"));
    assertTrue(searchText.contains("memory"));
}

// apex-agent/src/test/java/org/gemo/apex/memory/search/PgVectorLiteralFormatterTest.java
@Test
void formatShouldProducePgVectorLiteral() {
    assertEquals("[0.1,0.2,0.3]", PgVectorLiteralFormatter.format(new float[] {0.1f, 0.2f, 0.3f}));
}

// apex-agent/src/test/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingServiceTest.java
@Test
void embedShouldReturnNullWhenTextIsBelowConfiguredThreshold() {
    EmbeddingModel model = mock(EmbeddingModel.class);
    MemoryProperties properties = new MemoryProperties();
    properties.getSearch().setMinEmbeddingTextLength(8);
    SpringAiMemoryEmbeddingService service = new SpringAiMemoryEmbeddingService(model, properties);

    assertNull(service.embed("short"));
    verifyNoInteractions(model);
}
```

- [ ] **Step 2: Run the new search write-path tests to verify the classes do not exist yet**

Run:

```bash
mvn -q "-Dtest=SearchIndexTextBuilderTest,PgVectorLiteralFormatterTest,SpringAiMemoryEmbeddingServiceTest,PostgresSearchIndexUpdaterTest" test
```

Expected:

- compile failures for missing classes and methods

- [ ] **Step 3: Implement search text building, embedding abstraction, and JDBC-side index refresh**

```java
// apex-agent/src/main/java/org/gemo/apex/memory/search/SearchIndexTextBuilder.java
@Component
public class SearchIndexTextBuilder {

    public String buildDialogueMessageText(Message message, String content, String toolName) {
        return normalize(joinNonBlank(content, toolName));
    }

    public String buildSummaryText(String content) {
        return normalize(content);
    }

    public String buildExecutionHistoryText(MemoryItem item) {
        return normalize(joinNonBlank(item.getTitle(), item.getContent(), item.getTopicKey(), item.getStructuredPayload()));
    }

    private String joinNonBlank(String... parts) {
        return Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }
}

// apex-agent/src/main/java/org/gemo/apex/memory/search/MemoryEmbeddingService.java
public interface MemoryEmbeddingService {
    float[] embed(String text);
}

// apex-agent/src/main/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingService.java
@Component
@ConditionalOnBean(EmbeddingModel.class)
public class SpringAiMemoryEmbeddingService implements MemoryEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final MemoryProperties memoryProperties;

    public SpringAiMemoryEmbeddingService(EmbeddingModel embeddingModel, MemoryProperties memoryProperties) {
        this.embeddingModel = embeddingModel;
        this.memoryProperties = memoryProperties;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() < memoryProperties.getSearch().getMinEmbeddingTextLength()) {
            return null;
        }
        return embeddingModel.embed(text);
    }
}

// apex-agent/src/main/java/org/gemo/apex/memory/search/PostgresSearchIndexUpdater.java
@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class PostgresSearchIndexUpdater {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresSearchIndexUpdater(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void refreshDialogueMessage(long id, String searchText, float[] embedding) {
        jdbcTemplate.update("""
                UPDATE agent_session_dialogue_message
                   SET search_text = :searchText,
                       embedding = CAST(:embedding AS vector)
                 WHERE id = :id
                """, Map.of("id", id, "searchText", searchText,
                "embedding", PgVectorLiteralFormatter.format(embedding)));
    }

    public void refreshDialogueSummary(String sessionId, String searchText, float[] embedding) {
        jdbcTemplate.update("""
                UPDATE agent_session_dialogue_summary
                   SET search_text = :searchText,
                       embedding = CAST(:embedding AS vector)
                 WHERE session_id = :sessionId
                """, Map.of("sessionId", sessionId, "searchText", searchText,
                "embedding", PgVectorLiteralFormatter.format(embedding)));
    }

    public void refreshExecutionHistory(String id, String searchText, float[] embedding) {
        jdbcTemplate.update("""
                UPDATE user_execution_history_memory
                   SET search_text = :searchText,
                       embedding = CAST(:embedding AS vector)
                 WHERE id = :id
                """, Map.of("id", id, "searchText", searchText,
                "embedding", PgVectorLiteralFormatter.format(embedding)));
    }
}
```

```java
// apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java
entity.setSearchText(searchIndexTextBuilder.buildDialogueMessageText(message, message.getText(),
        MessageEntityConverter.resolveToolName(message)));

entity.setSearchText(searchIndexTextBuilder.buildSummaryText(summaryMessage.getText()));

// apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/MemoryItemEntityConverter.java
entity.setSearchText(searchIndexTextBuilder.buildExecutionHistoryText(item));
```

```java
// apex-agent/src/main/java/org/gemo/apex/memory/session/JdbcSessionContextStore.java
for (AgentSessionDialogueMessageEntity entity : SessionContextEntityConverter.toDialogueEntities(sessionId,
        turnNo, baseSortNo, messages)) {
    dialogueMessageMapper.insert(entity);
    try {
        searchIndexUpdater.refreshDialogueMessage(entity.getId(), entity.getSearchText(),
                memoryEmbeddingService.embed(entity.getSearchText()));
    } catch (RuntimeException ex) {
        log.warn("Failed to refresh dialogue message search index, sessionId={}, messageId={}",
                sessionId, entity.getId(), ex);
    }
}

if (summaryEntity != null) {
    try {
        searchIndexUpdater.refreshDialogueSummary(sessionId, summaryEntity.getSearchText(),
                memoryEmbeddingService.embed(summaryEntity.getSearchText()));
    } catch (RuntimeException ex) {
        log.warn("Failed to refresh dialogue summary search index, sessionId={}", sessionId, ex);
    }
}

// apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryWriteRepository.java
if (existing == null) {
    entity.setId(item.getId() != null ? item.getId() : IdUtil.simpleUUID());
    entity.setCreateTime(LocalDateTime.now());
    executionHistoryMemoryMapper.insert(entity);
} else {
    entity.setId(existing.getId());
    entity.setCreateTime(existing.getCreateTime());
    executionHistoryMemoryMapper.updateById(entity);
}
try {
    searchIndexUpdater.refreshExecutionHistory(entity.getId(), entity.getSearchText(),
            memoryEmbeddingService.embed(entity.getSearchText()));
} catch (RuntimeException ex) {
    log.warn("Failed to refresh execution history search index, id={}", entity.getId(), ex);
}
```

- [ ] **Step 4: Run the new search write-path tests and the affected persistence tests**

Run:

```bash
mvn -q "-Dtest=SearchIndexTextBuilderTest,PgVectorLiteralFormatterTest,SpringAiMemoryEmbeddingServiceTest,PostgresSearchIndexUpdaterTest,SessionContextEntityConverterTest,InMemorySessionContextStoreTest" test
```

Expected:

- PASS
- embedding failure tests prove writes continue when the embedding call throws or returns null

- [ ] **Step 5: Commit the search metadata write path**

```bash
git add apex-agent/src/main/java/org/gemo/apex/memory/search apex-agent/src/main/java/org/gemo/apex/memory/config/MemoryConfiguration.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverter.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/convert/MemoryItemEntityConverter.java apex-agent/src/main/java/org/gemo/apex/memory/session/JdbcSessionContextStore.java apex-agent/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryWriteRepository.java apex-agent/src/test/java/org/gemo/apex/memory/search apex-agent/src/test/java/org/gemo/apex/memory/persistence/convert/SessionContextEntityConverterTest.java apex-agent/src/test/java/org/gemo/apex/memory/session/InMemorySessionContextStoreTest.java
git commit -m "feat: persist search text and embeddings for memory storage"
```

## Task 4: Add Hybrid `session_search` Repository, Service, And Built-In Tool

**Files:**
- Modify: `apex-agent/src/main/java/org/gemo/apex/constant/ToolNames.java`
- Modify: `apex-agent/src/main/java/org/gemo/apex/component/tool/BuiltInToolProvider.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchQuery.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchHit.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchResult.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchScope.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/PostgresSessionSearchRepository.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchService.java`
- Create: `apex-agent/src/main/java/org/gemo/apex/tool/SessionSearchTool.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/memory/search/SessionSearchServiceTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/tool/SessionSearchToolTest.java`
- Test: `apex-agent/src/test/java/org/gemo/apex/component/tool/BuiltInToolProviderTest.java`

- [ ] **Step 1: Write failing tests for tool registration and service behavior**

```java
// apex-agent/src/test/java/org/gemo/apex/component/tool/BuiltInToolProviderTest.java
@Test
void initShouldRegisterSessionSearchTool() {
    BuiltInToolProvider provider = new BuiltInToolProvider(new SessionSearchTool(mock(SessionSearchService.class)));

    provider.init();

    List<String> toolNames = provider.getBuiltInTools().stream()
            .map(tool -> tool.getToolDefinition().name())
            .toList();
    assertTrue(toolNames.contains("session_search"));
}

// apex-agent/src/test/java/org/gemo/apex/memory/search/SessionSearchServiceTest.java
@Test
void searchShouldMergeAndDeduplicateRepositoryHits() {
    SessionSearchRepository repository = mock(SessionSearchRepository.class);
    MemoryEmbeddingService embeddingService = mock(MemoryEmbeddingService.class);
    when(embeddingService.embed("find previous fix")).thenReturn(new float[] {0.1f, 0.2f});
    when(repository.search(any(), any(), any())).thenReturn(List.of(
            new SessionSearchHit("dialogue_message", "session-1", "message-1", 2, 3L, "user", 0.91,
                    new SessionSearchHit.ScoreBreakdown(0.8, 0.95, 0.91), "snippet", null),
            new SessionSearchHit("dialogue_message", "session-1", "message-1", 2, 3L, "user", 0.90,
                    new SessionSearchHit.ScoreBreakdown(0.75, 0.94, 0.90), "snippet", null)));

    SessionSearchResult result = new SessionSearchService(repository, embeddingService, new MemoryProperties())
            .search(new SessionSearchQuery("find previous fix", null, 8, "hybrid", true, true),
                    new SessionSearchScope("user-1", "agent-1"));

    assertEquals(1, result.getHits().size());
}
```

- [ ] **Step 2: Run the tool/search tests to verify the new classes are absent**

Run:

```bash
mvn -q "-Dtest=BuiltInToolProviderTest,SessionSearchServiceTest,SessionSearchToolTest" test
```

Expected:

- compile failures for `SessionSearchTool`, `SessionSearchService`, and `session_search`

- [ ] **Step 3: Implement the hybrid search contract and built-in tool**

```java
// apex-agent/src/main/java/org/gemo/apex/constant/ToolNames.java
public static final String SESSION_SEARCH = "session_search";

// apex-agent/src/main/java/org/gemo/apex/tool/SessionSearchTool.java
public class SessionSearchTool {

    private final SessionSearchService sessionSearchService;

    @Tool(name = ToolNames.SESSION_SEARCH, description = "Search persisted dialogue messages and summaries from prior session history.")
    public SessionSearchResult session_search(SessionSearchQuery query, ToolContext toolContext) {
        SuperAgentContext context = (SuperAgentContext) toolContext.getContext().get(ToolContextKeys.SESSION_CONTEXT);
        SessionSearchScope scope = new SessionSearchScope(context.getUserId(), context.getAgentKey());
        return sessionSearchService.search(query, scope);
    }
}

// apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchService.java
public SessionSearchResult search(SessionSearchQuery query, SessionSearchScope scope) {
    float[] queryEmbedding = requiresVector(query.searchMode()) ? memoryEmbeddingService.embed(query.query()) : null;
    List<SessionSearchHit> hits = sessionSearchRepository.search(query, scope, queryEmbedding);
    List<SessionSearchHit> merged = deduplicateAndSort(hits, normalizedLimit(query.limit()));
    return new SessionSearchResult(query.query(), merged);
}

// apex-agent/src/main/java/org/gemo/apex/memory/search/PostgresSessionSearchRepository.java
private static final String HYBRID_SQL = """
WITH scoped_messages AS (
    SELECT m.id::text AS hit_id,
           'dialogue_message' AS source_type,
           s.session_id,
           m.turn_no,
           m.sort_no,
           m.role,
           m.content AS snippet,
           ts_rank_cd(m.search_vector, websearch_to_tsquery(:ftsConfig, :query)) AS fts_score,
           CASE WHEN :queryEmbedding IS NULL OR m.embedding IS NULL THEN 0
                ELSE 1 - (m.embedding <=> CAST(:queryEmbedding AS vector)) END AS vector_score,
           m.create_time
      FROM agent_session_dialogue_message m
      JOIN agent_session s ON s.session_id = m.session_id
     WHERE s.user_id = :userId
       AND s.agent_key = :agentKey
       AND (:sessionId IS NULL OR s.session_id = :sessionId)
)
SELECT * FROM scoped_messages
ORDER BY (0.45 * fts_score + 0.55 * vector_score) DESC
LIMIT :limit
""";
```

```java
// apex-agent/src/main/java/org/gemo/apex/component/tool/BuiltInToolProvider.java
public BuiltInToolProvider(SessionSearchTool sessionSearchTool) {
    this.sessionSearchTool = sessionSearchTool;
}

MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
        .toolObjects(
                new AskHumanTool(),
                new WritePlanTool(),
                new UpdatePlanTool(),
                sessionSearchTool)
        .build();
```

- [ ] **Step 4: Run the tool/search tests plus the factory regression tests**

Run:

```bash
mvn -q "-Dtest=BuiltInToolProviderTest,SessionSearchServiceTest,SessionSearchToolTest,SuperAgentFactoryTest" test
```

Expected:

- PASS
- built-in tool list contains `session_search`
- service tests prove result deduplication and limit clamping

- [ ] **Step 5: Commit the explicit search tool slice**

```bash
git add apex-agent/src/main/java/org/gemo/apex/constant/ToolNames.java apex-agent/src/main/java/org/gemo/apex/component/tool/BuiltInToolProvider.java apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchQuery.java apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchHit.java apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchResult.java apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchScope.java apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchRepository.java apex-agent/src/main/java/org/gemo/apex/memory/search/PostgresSessionSearchRepository.java apex-agent/src/main/java/org/gemo/apex/memory/search/SessionSearchService.java apex-agent/src/main/java/org/gemo/apex/tool/SessionSearchTool.java apex-agent/src/test/java/org/gemo/apex/memory/search/SessionSearchServiceTest.java apex-agent/src/test/java/org/gemo/apex/tool/SessionSearchToolTest.java apex-agent/src/test/java/org/gemo/apex/component/tool/BuiltInToolProviderTest.java apex-agent/src/test/java/org/gemo/apex/core/SuperAgentFactoryTest.java
git commit -m "feat: add hybrid session search tool"
```

## Task 5: Finish Documentation And Full Regression Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/快速开始.md`
- Verify: `docs/superpowers/specs/2026-04-27-postgres-session-search-design.md`

- [ ] **Step 1: Write the failing doc expectations as a checklist in the PR working notes**

```text
- README must say JDBC durable search support is PostgreSQL-first
- README must mention `CREATE EXTENSION IF NOT EXISTS vector`
- 快速开始 must show `memory-schema-postgresql.sql` as mandatory before JDBC startup
- 快速开始 must list the new `apex.memory.search.*` properties
```

- [ ] **Step 2: Run the targeted regression suite before editing docs**

Run:

```bash
mvn -q "-Dtest=SuperAgentFactoryTest,DefaultConversationMemoryManagerTest,InMemorySessionContextStoreTest,SessionContextEntityConverterTest,BuiltInToolProviderTest,SearchIndexTextBuilderTest,PgVectorLiteralFormatterTest,SpringAiMemoryEmbeddingServiceTest,PostgresSearchIndexUpdaterTest,SessionSearchServiceTest,SessionSearchToolTest" test
```

Expected:

- PASS

- [ ] **Step 3: Update the user-facing docs with the new runtime contract**

```md
<!-- README.md -->
- 默认记忆存储仍可为 `in-memory`
- 当 `apex.memory.store.type=jdbc` 且需要 `session_search` 时，官方支持数据库为 PostgreSQL
- 启动前需执行 `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
- 数据库需启用 `pgvector` 扩展
```

```md
<!-- docs/快速开始.md -->
如果切到 `jdbc`，需要先执行：

- `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
- 确保 PostgreSQL 已安装 `vector` 扩展

新增可选配置：

- `apex.memory.search.embedding-dimension`
- `apex.memory.search.text-search-config`
- `apex.memory.search.default-session-search-limit`
- `apex.memory.search.max-session-search-limit`
- `apex.memory.search.min-embedding-text-length`
```

- [ ] **Step 4: Run the full targeted regression suite one more time after docs changes**

Run:

```bash
mvn -q "-Dtest=SuperAgentFactoryTest,DefaultConversationMemoryManagerTest,InMemorySessionContextStoreTest,SessionContextEntityConverterTest,BuiltInToolProviderTest,SearchIndexTextBuilderTest,PgVectorLiteralFormatterTest,SpringAiMemoryEmbeddingServiceTest,PostgresSearchIndexUpdaterTest,SessionSearchServiceTest,SessionSearchToolTest" test
```

Expected:

- PASS

- [ ] **Step 5: Commit the docs and verification slice**

```bash
git add README.md docs/快速开始.md
git commit -m "docs: document postgres session search setup"
```

## Self-Review

### Spec coverage

- Remove automatic recent-N recall:
  - Task 1
- PostgreSQL search columns and indexes for the three required tables:
  - Task 2
- Synchronous embedding generation inside `apex-agent`:
  - Task 3
- `session_search` as the only explicit search tool:
  - Task 4
- No `memory_search` tool:
  - enforced by Task 4 scope
- Prompt regression so history is no longer auto-injected:
  - Task 1 and Task 5 verification
- No historical migration/backfill:
  - intentionally absent from all tasks

### Placeholder scan

- No `TODO` / `TBD`
- Every task includes exact files
- Every task includes explicit test commands
- Every code-changing step includes concrete code snippets

### Type consistency

- Search config lives under `MemoryProperties.SearchProperties`
- Tool name constant is `ToolNames.SESSION_SEARCH`
- Tool input/output types are `SessionSearchQuery`, `SessionSearchHit`, and `SessionSearchResult`
- Scope type is `SessionSearchScope`
- Write-side embedding abstraction is `MemoryEmbeddingService`
