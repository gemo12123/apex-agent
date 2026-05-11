# Skill Experience Learning Design

Date: 2026-05-11
Status: Drafted for review

## 1. Background

`apex-agent` already supports filesystem-backed Skills loaded from `SKILL.md`, exposed through the built-in `activate_skill` tool, and returned to the model as plain instruction content.

The current behavior is static:

- a Skill is loaded from disk
- `activate_skill` returns the raw Skill body
- no execution evidence is accumulated per Skill
- no historical Skill-specific experience is injected on later activations

The memory subsystem already solves adjacent problems such as:

- persisted session dialogue storage
- prompt-template based extraction
- asynchronous/background processing through a scheduler

This design adds a dedicated Skill experience learning module without forcing Skill experience into the existing generic memory type model.

## 2. Goal

Add a dedicated `org.gemo.apex.skills.learning` module that:

- records every successful `activate_skill` usage
- periodically checks whether a given `agentKey + skillName` has reached a configurable usage threshold
- extracts Skill-specific experience from the corresponding historical dialogue
- stores the latest aggregated experience per `agentKey + skillName`
- appends that experience to the activated Skill body on later `activate_skill` calls

The design must follow these product constraints:

- threshold checking happens only in a scheduled task, not inline during activation
- the extraction prompt is loaded through the same prompt-loading mechanism already used by memory extraction
- existing Skill experience is included in the extraction prompt so the model can re-analyze and rewrite the experience
- short sessions use the full session message list
- long sessions use a fixed message window around the `activate_skill` position
- experience is appended as a fixed Markdown section at the end of the Skill body
- the appended section must warn that the experience is advisory and should not be followed mechanically

## 3. Scope

### 3.1 In scope

- dedicated usage and experience persistence for Skills
- `post-tool-call` hook based Skill usage recording
- `post-tool-call` hook based Skill experience augmentation
- daily scheduled threshold scan
- per-usage message collection and validation
- LLM-based experience regeneration
- overwriting the latest experience for the same `agentKey + skillName`

### 3.2 Out of scope

- changing the existing generic memory type hierarchy
- immediate extraction when threshold is reached
- user-scoped Skill experience
- vector search or full-text search for Skill experience in this version
- frontend management UI for Skill experience
- backfilling historical Skill activations from old sessions

## 4. Key Decisions

### 4.1 Scope key

Skill experience is stored by:

- `agentKey + skillName`

This keeps experience isolated per Agent while allowing reuse across sessions and users under the same Agent.

### 4.2 Trigger model

Threshold evaluation runs only in a daily scheduled task.

Each successful `activate_skill` call records a usage row. Nothing else happens inline besides:

- appending existing experience to the returned Skill body
- persisting the usage record

### 4.3 Batch reset model

After a successful extraction run:

- delete the usage records that actually participated in extraction
- delete the usage records that failed validation

The next batch starts from newly accumulated usage rows only.

### 4.4 Message sampling rule

For each usage:

- if the session message count is at or below a configurable threshold, use the full session message list
- otherwise, use a fixed message-count window around the `activate_skill` activation point

The first version uses message-count windows rather than token-based trimming.

## 5. Data Model

## 5.1 `skill_usage_record`

Purpose: accumulate concrete Skill activations until a scheduled extraction batch is eligible.

Fields:

- `id`
- `agent_key`
- `skill_name`
- `session_id`
- `turn_no`
- `activation_message_sort_no`
- `created_time`

Behavior:

- insert one row for every successful `activate_skill`
- do not deduplicate rows
- rows are deleted only after a successful extraction batch handling

## 5.2 `skill_experience_memory`

Purpose: store the latest rewritten experience for one `agentKey + skillName`.

Fields:

- `id`
- `agent_key`
- `skill_name`
- `content`
- `version_no`
- `create_time`
- `update_time`

Constraints:

- unique key on `agent_key + skill_name`

Behavior:

- one latest row per `agentKey + skillName`
- overwrite content on each successful regeneration
- increment `version_no` on each successful rewrite

## 6. Runtime Components

All new code should live under:

- `org.gemo.apex.skills.learning`

Recommended components:

- `SkillUsageRecordRepository`
- `SkillExperienceMemoryRepository`
- `SkillUsageRecorderHook`
- `SkillExperienceAugmentHook`
- `SkillExperienceScheduler`
- `SkillUsageBatchService`
- `SkillUsageMessageCollector`
- `SkillExperiencePromptService`
- `SkillExperienceExtractor`

## 7. Activation-Time Behavior

## 7.1 Hook usage

Both activation-time behaviors use the existing `post-tool-call` hook runtime for `activate_skill`.

Hook order should be:

1. `skillExperienceAugmentHook`
2. `skillUsageRecordHook`

Reason:

- the returned Skill body should be augmented first
- usage persistence should not risk interfering with result replacement

## 7.2 `skillExperienceAugmentHook`

Applies only when:

- `toolName == activate_skill`

Inputs:

- `agentKey` from `SuperAgentContext`
- `skillName` from hook arguments
- current tool result text from `PostToolCallHookContext.currentResult`

Behavior:

1. load existing experience for `agentKey + skillName`
2. if no experience exists, keep the original result
3. if experience exists, append a fixed Markdown section to the end of the current activated Skill body
4. return `REPLACE_RESULT`

Suggested appended section:

```md
# Skill经验

以下经验来自该 Skill 在当前 Agent 下的历史使用总结，仅供参考。
请结合当前任务判断，不要完全依赖这些经验；当经验与当前上下文冲突时，以当前任务事实为准。

{experience_content}
```

The hook must preserve the existing `<activated_skill>` wrapper returned by `activate_skill`.

## 7.3 `skillUsageRecordHook`

Applies only when:

- `toolName == activate_skill`

Inputs:

- `agentKey`
- `sessionId`
- `turnNo`
- `skillName`
- activation message location

Behavior:

1. resolve a stable `activation_message_sort_no`
2. insert one usage row
3. keep the current tool result unchanged

This hook must run only after the tool invocation has succeeded.

## 8. Scheduled Extraction Behavior

## 8.1 Scheduler

Run once daily at a configurable cron time, defaulting to early morning.

Flow:

1. query grouped usage counts by `agent_key + skill_name`
2. skip groups below threshold
3. process each eligible group independently

Threshold semantics:

- no time window filter
- only current accumulated row count matters

## 8.2 Group processing

For one eligible `agentKey + skillName` group:

1. load the usage rows for that group
2. validate each usage before collecting messages
3. separate rows into:
   - valid rows
   - invalid rows
4. if valid rows are fewer than threshold after validation, do nothing and keep valid rows
5. if valid rows still meet threshold:
   - collect message slices
   - load existing Skill experience
   - invoke experience extraction
   - upsert latest experience
   - delete valid rows that actually participated
   - delete invalid rows found in this run

## 9. Usage Validation

Before collecting messages for a usage row, the system must verify that the referenced activation point really corresponds to the target Skill.

Validation rule:

- the session content around `activation_message_sort_no` must show that `activate_skill` was called for the same `skill_name`

If validation fails:

- mark that usage row as invalid for this batch
- exclude it from extraction input
- log a warning with enough identifiers for tracing

The purpose is to protect against:

- wrong sort number capture
- stale or corrupted usage rows
- future bugs in activation position resolution

## 10. Message Collection Rules

## 10.1 Session length rule

For each validated usage:

- short session: use the full session message list
- long session: use a fixed-size message window around the activation point

The cutoff is configurable and based on message count.

## 10.2 Long session window rule

For long sessions:

- collect `N` messages before the activation point
- collect the activation point message
- collect `M` messages after the activation point

Both `N` and `M` are configurable counts.

Window boundaries should clamp to the available message range instead of failing.

## 10.3 Multiple activations in the same session

If the same Skill is activated multiple times in one session:

- treat each usage row independently
- collect its own full-session or windowed slice based on session size and activation point

Do not merge multiple usage rows into one monolithic session sample before collection.

## 11. Prompt Loading And Extraction

## 11.1 Prompt loading

The Skill experience extraction prompt must use the same loading pattern already used by memory extraction prompt loading.

That means:

- configure a prompt path under application properties
- load the prompt file from the configured path
- avoid hardcoding prompt content in Java

## 11.2 Prompt variables

The prompt should render at least:

- `agentKey`
- `skillName`
- `existingExperience`
- `conversationSlices`

## 11.3 Extraction semantics

Extraction is regenerative rather than append-only.

The model should receive:

- current accumulated conversation evidence
- the existing stored experience for the same `agentKey + skillName`

And it should produce:

- one rewritten latest experience body

This ensures the experience can evolve instead of only growing by concatenation.

## 12. Failure Handling

## 12.1 Activation-time hooks

`skillExperienceAugmentHook` failure:

- keep the original `activate_skill` result
- log warning
- do not block the request

`skillUsageRecordHook` failure:

- keep the current result
- log warning
- do not block the request

## 12.2 Scheduled extraction

If extraction fails for a group:

- do not delete valid usage rows
- do not delete invalid usage rows in that failed group
- log warning or error
- retry on the next scheduled run

Only after experience upsert succeeds should the batch delete rows.

## 13. Idempotency And Consistency

- process each `agentKey + skillName` group independently
- use group-level mutual exclusion to avoid concurrent duplicate processing
- upsert `skill_experience_memory` by unique key `agent_key + skill_name`
- increment `version_no` on each successful overwrite
- delete rows only by the exact usage id sets determined during the successful run

Deletion after a successful batch includes:

- all valid usage ids that were actually used for extraction
- all invalid usage ids discovered in that run

## 14. Configuration

Add a dedicated configuration tree such as:

- `apex.skills.learning.enabled`
- `apex.skills.learning.usage-threshold`
- `apex.skills.learning.daily-cron`
- `apex.skills.learning.long-session-message-threshold`
- `apex.skills.learning.activation-window-before`
- `apex.skills.learning.activation-window-after`
- `apex.skills.learning.experience-prompt`
- `apex.skills.learning.experience-section-title`

The first version may keep the fixed Markdown title default as:

- `Skill经验`

## 15. Testing Requirements

Add tests for:

- `post-tool-call` augmentation replaces the `activate_skill` result correctly
- augmentation keeps the original result when no experience exists
- usage hook inserts rows only for successful `activate_skill`
- scheduler skips groups below threshold
- usage validation rejects mismatched activation points
- short sessions use full message lists
- long sessions use fixed windows around activation
- extraction prompt includes existing experience
- successful extraction upserts experience and deletes both valid-participating usage rows and invalid rows
- failed extraction preserves usage rows

## 16. Acceptance Criteria

This design is complete when:

- Skill usage is recorded for successful `activate_skill` calls
- existing Skill experience is appended to activated Skill bodies via `post-tool-call` hook
- daily scheduled processing checks threshold by accumulated usage count only
- short sessions use full message history and long sessions use activation-centered windows
- extraction prompt is loaded through the same mechanism style used by memory extraction
- existing Skill experience is included in the extraction prompt
- the latest Skill experience is stored by `agentKey + skillName`
- a successful batch deletes both extracted valid usage rows and validation-failed usage rows
- no activation-time failure blocks normal Skill usage
