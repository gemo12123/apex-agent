# Agent Session Fixed Messages Design

Date: 2026-04-27
Status: Approved for planning

## 1. Background

The current `apex-agent` conversation model builds `fixedMessages` only in runtime memory.

`DefaultConversationMemoryManager.refreshFixedMessages()` reconstructs that list every round from:

- the stage system prompt
- the current user id message
- the rendered recall text from `MemoryRecallPackage`

That runtime behavior is correct for model invocation, but it is not persisted in `agent_session`.

At the same time, asynchronous memory extraction currently reconstructs its input from persisted dialogue only. `MemoryLifecycleManager` loads the session context and separately loads raw dialogue messages through `SessionContextStore.loadAllRawDialogueMessages(sessionId)`.

As a result, background extraction cannot see the latest fixed prompt context that was present when the round executed.

## 2. Goal

Persist the latest complete `fixedMessages` snapshot on `agent_session` so memory extraction can assemble its message list as:

- current latest persisted `fixedMessages`
- persisted raw dialogue messages

The stored snapshot is for memory extraction only.

The existing per-round `refreshFixedMessages()` runtime logic must remain unchanged and must not start reading prompt text back from the database.

## 3. Non-Goals

- changing how runtime model messages are built for normal agent execution
- storing fixed messages in `agent_session_dialogue_message`
- changing dialogue compaction, summary generation, turn numbering, or sort numbering
- adding compatibility handling for historical sessions without `fixed_messages`
- deduplicating recalled memory text that may appear inside fixed messages

## 4. Chosen Approach

Add a dedicated `fixed_messages` column to `agent_session` and store the latest full `List<Message>` snapshot as JSON.

The design keeps the responsibilities separated:

- runtime prompt assembly still uses the in-memory `context.fixedMessages` generated in the current round
- persistence stores the latest snapshot for later reuse
- memory extraction reconstructs its input from `fixedMessages + raw dialogue messages`
- raw dialogue storage remains limited to true dialogue messages

This avoids polluting dialogue tables and does not alter compaction or message ordering semantics.

## 5. Data Model Changes

### 5.1 Database schema

Add one column to `agent_session`:

- `fixed_messages TEXT`

Meaning:

- JSON payload of the latest persisted full `fixedMessages` snapshot
- the payload represents the complete list, not a delta and not a per-turn history

### 5.2 `AgentSessionEntity`

Add one field:

- `fixedMessages`

Mapping:

- `@TableField("fixed_messages")`

### 5.3 Serialization format

The `fixed_messages` payload should reuse the same message serialization strategy already used for message persistence, so fixed message JSON does not introduce a second incompatible encoding rule.

This means the implementation should rely on the existing message conversion utilities instead of inventing a new ad hoc prompt snapshot structure.

## 6. Persistence Semantics

### 6.1 Save path

`SessionContextEntityConverter.toSessionEntity()` writes `context.getFixedMessages()` into `AgentSessionEntity.fixedMessages`.

`JdbcSessionContextStore.save()` and `InMemorySessionContextStore.save()` continue to treat this as part of normal session metadata persistence.

No extra save flow is introduced just for fixed messages.

### 6.2 Load path

`SessionContextEntityConverter.fromEntities()` restores `AgentSessionEntity.fixedMessages` back into `SuperAgentContext.fixedMessages`.

After session load:

- the restored list represents the latest persisted fixed message snapshot
- it is available for background memory extraction
- it does not override the existing behavior where runtime execution may later refresh the list again

### 6.3 Runtime refresh behavior

`DefaultConversationMemoryManager.refreshFixedMessages()` remains the single source of truth for building the current round's runtime fixed message list.

It continues to derive the list from the current prompt inputs and must not start reading prompt text from `agent_session.fixed_messages`.

The database field is a persisted snapshot, not a configuration source.

## 7. Memory Extraction Semantics

### 7.1 Raw dialogue contract stays unchanged

`SessionContextStore.loadAllRawDialogueMessages(sessionId)` keeps its current meaning:

- it returns only persisted raw dialogue messages
- it does not include fixed messages
- it does not include the compressed summary message

That contract should remain stable so existing callers do not receive mixed message types unexpectedly.

### 7.2 Extraction input assembly

Before invoking extraction, the runtime assembles a new list:

1. latest persisted `context.fixedMessages`
2. all persisted raw dialogue messages ordered by `sortNo`

This assembled list becomes the extraction input for:

- execution history extraction
- agent experience extraction
- long-term profile extraction

### 7.3 Compaction pre-flush extraction

`MemoryLifecycleManager.preFlushBeforeCompaction()` should apply the same idea, but use the current in-memory `context.getFixedMessages()` directly because it already has the latest runtime snapshot and does not require a database reload.

The compaction pre-flush input therefore becomes:

- current in-memory fixed messages
- the `oldDialogueMessages` slice being extracted before compaction

### 7.4 Summary handling

The assembled extraction input should not append `latestCompressedMessage`.

Reason:

- background extraction already reads the full raw dialogue history
- adding the summary would duplicate the same historical content in a second form
- duplicate history makes extraction prompts noisier and less deterministic

## 8. Code Boundary Changes

The design expects changes in the following areas:

- `memory-schema-postgresql.sql`
- `AgentSessionEntity`
- `SessionContextEntityConverter`
- `JdbcSessionContextStore`
- `InMemorySessionContextStore`
- `MemoryLifecycleManager`
- `MemoryExtractionService` or a nearby helper used to assemble extraction messages

The `extractXxxCandidates(context, List<Message> ...)` overloads may keep their existing signatures, but the second parameter should be treated as "messages prepared for extraction" rather than "raw dialogue only".

If names or comments still say `rawMessages`, they should be clarified so the code does not misdescribe the new behavior.

## 9. Testing Requirements

### 9.1 Persistence tests

Add or update tests to verify:

- `fixed_messages` is persisted on session save
- `fixed_messages` is restored on session load
- restored fixed messages preserve order and message role
- `InMemorySessionContextStore` matches JDBC semantics for this field

### 9.2 Converter tests

Add or update tests to verify:

- `SessionContextEntityConverter` correctly round-trips fixed messages
- the restored messages are usable `Message` instances, not only raw JSON text

### 9.3 Extraction orchestration tests

Add or update tests to verify:

- execution history extraction receives `fixedMessages + raw dialogue messages`
- experience extraction receives `fixedMessages + raw dialogue messages`
- profile extraction receives `fixedMessages + raw dialogue messages`
- compaction pre-flush extraction receives current runtime fixed messages plus the compaction slice

### 9.4 Regression boundary tests

Add or update tests to verify:

- `buildModelMessages()` ordering is unchanged
- raw dialogue load APIs still return dialogue only
- compaction and summary logic are unaffected by the new field

## 10. Compatibility Boundary

This change does not include historical compatibility handling.

The implementation may assume that after rollout:

- sessions are saved with `fixed_messages`
- extraction paths that depend on fixed messages operate on sessions produced by the new persistence model

No backfill, fallback reconstruction, or legacy data migration behavior is required in this version.

## 11. Risks and Mitigations

Risk: `fixed_messages` may include recalled memory text, so extraction may see both recalled memory guidance and later dialogue that references the same information.

Mitigation:

- accept this as intentional behavior in this version
- keep the rule explicit that the stored snapshot is reused as-is for extraction

Risk: fixed message JSON may diverge from dialogue message JSON if a separate encoding strategy is introduced.

Mitigation:

- reuse the existing message serialization/conversion utilities
- keep one message encoding rule across persisted message-like payloads

Risk: future maintainers may confuse persisted fixed messages with the source of runtime prompt configuration.

Mitigation:

- document clearly in code and tests that `fixed_messages` is an extraction snapshot only
- keep `refreshFixedMessages()` as the only runtime prompt builder

## 12. Acceptance Criteria

This design is complete when:

- `agent_session.fixed_messages` stores the latest full fixed message snapshot
- session save/load round-trips `fixedMessages`
- runtime `refreshFixedMessages()` logic is unchanged
- memory extraction uses `fixedMessages + raw dialogue messages`
- raw dialogue persistence semantics remain unchanged
- no historical compatibility behavior is added in this change
