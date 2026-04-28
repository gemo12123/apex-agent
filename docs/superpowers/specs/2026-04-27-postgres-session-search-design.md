# Postgres Session Search Design

Date: 2026-04-27
Status: Approved for planning

## 1. Background

The current `apex-agent` memory recall path is built around automatic injection of the most recent memory items.

`MemoryRecallService` currently reads profile, execution-history, and experience memories through `MemoryReadRepository`, and both the JDBC and in-memory implementations return items ordered by `observedTime` descending with a fixed recall limit.

`DefaultConversationMemoryManager.refreshFixedMessages()` then renders that recall package into prompt text and injects it as a `UserMessage` on every round.

That behavior creates three problems:

- recall quality is weak because retrieval is recency-based rather than relevance-based
- the main conversation prompt grows by default even when historical context is not needed
- the current abstraction does not leave room for full-text search, vector search, or explicit search tools

The gap analysis for self-learning and self-improvement already identifies this as a core weakness: the system persists useful history, but it still cannot reliably find the right historical evidence when needed.

At the same time, the repository already leans toward PostgreSQL when JDBC storage is enabled:

- `application.yml` defaults the JDBC URL and driver to PostgreSQL
- the documented memory schema is already `memory-schema-postgresql.sql`

This design formalizes that direction and turns PostgreSQL into the durable search-capable storage base for JDBC memory.

## 2. Goal

When `apex.memory.store.type=jdbc`, the durable memory/session storage model should support PostgreSQL-based search infrastructure with:

- `tsvector` full-text search
- `pgvector` semantic vector search
- a single explicit `session_search` tool for historical session lookup
- a narrowed automatic recall path that keeps only profile and experience memories in the round prompt

The minimum required tables are:

- `agent_session_dialogue_message`
- `agent_session_dialogue_summary`
- `user_execution_history_memory`

The first two power the new explicit search tool.

The third gains the same durable search fields so the storage model is consistent and future search expansion does not require another schema redesign.

## 3. Non-Goals

- removing automatic recall entirely from the runtime
- automatically recalling execution-history memories into the round prompt
- adding a `memory_search` tool in this version
- automatically injecting searched historical content into every prompt
- building MMR, recall trace, consolidation jobs, or background re-index pipelines
- handling historical data migration or backfill
- removing JDBC as a configuration concept
- addressing the temporary MySQL dependency in `pom.xml`

## 4. Chosen Approach

The design keeps PostgreSQL-backed search, but adjusts the runtime boundary instead of removing recall completely:

- keep durable storage and indexing in PostgreSQL
- keep `MemoryRecallService` as the automatic recall entrypoint
- keep automatic prompt injection only for user profile memories and agent experience memories
- remove execution-history memories from the automatic recall path
- expose a single explicit `session_search` tool for historical retrieval
- generate embeddings synchronously inside `apex-agent` during writes

This gives Apex a hybrid model:

- lightweight always-on recall for high-signal durable profile/experience context
- explicit historical search for persisted dialogue history when the model chooses to search
- durable PostgreSQL indexing for messages, summaries, and execution-history rows

The JDBC abstraction remains in place, but the official search-capable implementation is PostgreSQL-specific. Non-PostgreSQL JDBC deployments are not part of the supported behavior for this design.

## 5. Search Boundary

### 5.1 Tool boundary

Only one new explicit tool is added:

- `session_search`

Its searchable sources are:

- `agent_session_dialogue_message`
- `agent_session_dialogue_summary`

No `memory_search` tool is added in this version.

### 5.2 Automatic recall boundary

The automatic memory recall path should remain in the main agent execution flow, but with a smaller scope.

Specifically:

- `MemoryRecallService` should continue to populate a runtime recall package
- `MemoryReadRepository` should remain available for recent-item recall reads
- `DefaultConversationMemoryManager.refreshFixedMessages()` should continue rendering recall text
- `MemoryRecallPackage` should remain as the runtime carrier object
- execution-history memories must be removed from this automatic recall package and from recall text rendering

After this change, the fixed message list should contain:

- the stage system prompt
- the current user id message
- optional rendered recall text for:
  - user profile memories
  - agent experience memories

Execution-history evidence should appear only when it is persisted/indexed for future use or when the model explicitly invokes `session_search`.

## 6. PostgreSQL Data Model Changes

### 6.1 Common indexable fields

Each of the three target tables should carry explicit search-support columns:

- `search_text TEXT`
- `search_vector tsvector`
- `embedding vector(<configured-dimension>)`

These are stored fields, not only expression indexes.

Reason:

- easier debugging of search inputs
- easier future re-indexing and regeneration workflows
- consistent application-side control over what text is embedded

### 6.2 `agent_session_dialogue_message`

Add:

- `search_text TEXT`
- `search_vector tsvector`
- `embedding vector(<configured-dimension>)`

Meaning:

- `search_text` is the normalized searchable text assembled from the message
- `search_vector` is the full-text index payload derived from `search_text`
- `embedding` is the semantic vector derived from `search_text`

Recommended supporting indexes:

- keep `idx_agent_session_dialogue_message_session_sort`
- keep `uk_agent_session_dialogue_message_session_sort`
- add `GIN(search_vector)`
- add vector ANN index for `embedding`

### 6.3 `agent_session_dialogue_summary`

Add:

- `search_text TEXT`
- `search_vector tsvector`
- `embedding vector(<configured-dimension>)`

Recommended indexes:

- `GIN(search_vector)`
- vector ANN index for `embedding`

### 6.4 `user_execution_history_memory`

Add:

- `search_text TEXT`
- `search_vector tsvector`
- `embedding vector(<configured-dimension>)`

Recommended indexes:

- keep business uniqueness index
- add `GIN(search_vector)`
- add vector ANN index for `embedding`
- optionally add a filter-friendly business index such as `(user_id, agent_key, observed_time desc)`

### 6.5 PostgreSQL extension requirements

The schema must explicitly require:

- `CREATE EXTENSION IF NOT EXISTS vector`

For full-text search, the first version may use PostgreSQL built-in text search support with a configurable text search configuration.

The design should not hardcode a Chinese-specific tokenizer implementation in this version. The configuration can start with a stable default and remain replaceable later.

## 7. Search Text and Embedding Semantics

### 7.1 Search text construction

A single dedicated builder should assemble `search_text` so the logic is not duplicated across repositories.

Suggested rules:

- dialogue messages: primarily `content`, optionally `tool_name` when useful for retrieval
- dialogue summaries: `content`
- execution history memory: `title + content + topic_key + relevant structured payload excerpts`

The builder should exclude noisy raw JSON dumps unless specific fields are intentionally extracted.

### 7.2 When embeddings are generated

Embeddings are generated synchronously by `apex-agent` during write/update flows.

The write path is:

1. build `search_text`
2. if `search_text` is eligible, generate embedding
3. write business fields plus `search_text`, `search_vector`, and `embedding`

### 7.3 Eligibility rules

The first version should be conservative.

Examples:

- blank text: no embedding
- extremely short/noisy text: embedding may be skipped
- tool/noise-only messages: prefer FTS support only unless the content is actually meaningful

### 7.4 Failure behavior

Embedding generation failure must not block the primary write.

If embedding generation fails:

- the row still persists
- `search_text` still persists
- `search_vector` still persists
- `embedding` may remain null
- the application logs a warning with enough context to trace the row type and id

This keeps FTS available even when semantic indexing is temporarily unavailable.

## 8. `session_search` Query Model

### 8.1 Tool contract

The tool should be explicit, read-only, and scoped to the current agent/user/session context.

Suggested input shape:

```json
{
  "query": "how did we previously solve the retrieval issue",
  "sessionId": "optional session filter",
  "limit": 8,
  "searchMode": "hybrid",
  "includeSummaries": true,
  "includeMessages": true
}
```

Suggested modes:

- `fts`
- `vector`
- `hybrid`

Default:

- `hybrid`

### 8.2 Result contract

Suggested output shape:

```json
{
  "query": "how did we previously solve the retrieval issue",
  "hits": [
    {
      "sourceType": "dialogue_message",
      "sessionId": "abc",
      "messageId": "123",
      "turnNo": 7,
      "sortNo": 42,
      "role": "user",
      "score": 0.83,
      "scoreBreakdown": {
        "fts": 0.71,
        "vector": 0.88,
        "hybrid": 0.83
      },
      "snippet": "...",
      "createTime": "2026-04-27T12:34:56"
    }
  ]
}
```

The result should retain enough evidence fields for both model use and debugging.

### 8.3 Query execution

The first version should use a two-path hybrid retrieval model:

- FTS path over `search_vector`
- vector path over `embedding`

The service should:

1. generate `queryEmbedding` synchronously
2. execute FTS search
3. execute vector search
4. merge and normalize results
5. deduplicate and trim to `limit`

The merge logic should live in the service layer rather than a single large SQL statement, because that keeps score tuning easier and code easier to test.

### 8.4 First-version scoring

The initial score model may be simple.

Example:

- `hybrid = 0.45 * normalizedFts + 0.55 * normalizedVector`

This is not a permanent scoring contract. It is only the initial tuning baseline.

## 9. Code Boundary Changes

### 9.1 Retain and narrow recall-centric pieces

The following areas should remain, but with narrower responsibilities:

- `MemoryRecallService`
- `MemoryReadRepository` recent-item search methods for profile and experience only
- `JdbcMemoryReadRepository`
- `InMemoryMemoryReadRepository`
- `MemoryRecallPackage`
- recall rendering in `DefaultConversationMemoryManager`

The automatic recall path should be updated so that:

- `MemoryRecallService` recalls only profile and experience memories
- `MemoryReadRepository` no longer exposes execution-history recall reads
- `DefaultConversationMemoryManager` no longer renders a `用户执行历史记忆` section
- `SuperAgentFactory` continues to attach the recall package during new-turn and resume flows

### 9.2 New search-focused components

Add:

- `SessionSearchQuery`
- `SessionSearchHit`
- `SessionSearchResult`
- `SessionSearchRepository`
- `PostgresSessionSearchRepository`
- `SessionSearchService`
- `SessionSearchTool`
- `SearchIndexTextBuilder`
- `MemoryEmbeddingService` or equivalent dedicated embedding abstraction

### 9.3 Tool registration

`session_search` should be registered as a built-in tool through the same `ToolCallback` path already used by the core built-in tool provider.

This keeps the integration model aligned with the existing tool architecture and avoids introducing a special one-off tool registration path.

## 10. Configuration Changes

### 10.1 Storage support statement

`apex.memory.store.type=jdbc` remains the toggle for durable storage.

However, the search-enhanced JDBC implementation designed here is supported only for PostgreSQL semantics.

This means:

- JDBC remains the public storage type name
- PostgreSQL is the official database target for this search design
- non-PostgreSQL JDBC deployments are outside the supported behavior boundary

The first version does not need to fail startup for non-PostgreSQL databases. It only needs the support boundary to be explicit in code comments, docs, and tests.

### 10.2 New search properties

Add memory search-related configuration, for example:

- embedding model selection/config reference
- embedding dimension
- text search configuration name
- default `session_search` limit
- max allowed `session_search` limit

These should live under the memory configuration tree rather than becoming ad hoc unrelated properties.

## 11. Write Path Changes

### 11.1 Dialogue message persistence

When dialogue messages are persisted:

- build `search_text`
- generate embedding if eligible
- persist all search columns together with the business row

### 11.2 Dialogue summary persistence

When summaries are saved:

- build `search_text`
- generate embedding if eligible
- persist all search columns with the summary row

### 11.3 Execution history memory persistence

When execution history memory is inserted or updated:

- rebuild `search_text`
- regenerate embedding if eligible
- persist the updated search columns together with the business row

The same rule applies to both create and update paths so content edits do not leave stale search artifacts behind.

## 12. Testing Requirements

### 12.1 Schema and persistence tests

Add or update tests to verify:

- the new search columns exist in the PostgreSQL schema definition
- converter/persistence code populates `search_text`
- search-aware writes regenerate indexable data after content changes

### 12.2 Embedding behavior tests

Add or update tests to verify:

- eligible rows request embeddings
- ineligible rows can skip embeddings
- embedding failure does not block writes

### 12.3 Tool and search tests

Add or update tests to verify:

- `session_search` can find dialogue messages
- `session_search` can find summaries
- hybrid merge returns stable, deduplicated hits
- `session_search` respects the current user/agent scope
- optional `sessionId` filter limits results correctly

### 12.4 Prompt regression tests

Add or update tests to verify:

- `DefaultConversationMemoryManager.refreshFixedMessages()` still injects recall text when profile/experience items exist
- round prompt construction still includes stage prompt and user id message
- automatic recall text never includes a `用户执行历史记忆` section
- `MemoryRecallService` never reads execution-history items for automatic recall

## 13. Risks and Mitigations

Risk: synchronous embeddings increase write latency.

Mitigation:

- keep the first version conservative about what gets embedded
- allow FTS-only fallback when embeddings fail

Risk: vector dimension and configured embedding model can drift apart.

Mitigation:

- define one explicit configured embedding dimension
- keep schema and embedding service wired to the same configuration source

Risk: full-text search quality for Chinese and mixed tool/code text may vary.

Mitigation:

- keep FTS configuration replaceable
- rely on hybrid retrieval rather than full-text search alone

Risk: automatic recall can still expand prompt size or drift back toward low-signal recency injection.

Mitigation:

- keep recall scope explicitly limited to profile and experience memories
- make the execution-history exclusion explicit in documentation and tests

## 14. Acceptance Criteria

This design is complete when:

- JDBC durable search support is defined around PostgreSQL
- `agent_session_dialogue_message`, `agent_session_dialogue_summary`, and `user_execution_history_memory` all store explicit full-text and vector search fields
- embeddings are generated synchronously by `apex-agent` during writes
- automatic prompt recall remains available only for profile and experience memories
- execution-history memories are excluded from automatic recall
- only `session_search` is exposed as the explicit historical search tool
- `session_search` searches persisted messages and summaries through hybrid FTS plus vector retrieval
- no historical migration or backfill behavior is introduced in this version
