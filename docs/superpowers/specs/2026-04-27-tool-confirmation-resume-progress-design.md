# Tool Confirmation Resume Progress Design

Date: 2026-04-27
Status: Approved for planning

## 1. Background

The current `TOOL_CONFIRMATION` restore path in `apex-agent` resumes a suspended tool call by re-entering the normal tool execution pipeline and skipping only the single confirmation hook bean that originally requested confirmation.

That behavior is implemented by persisting one field, `hookSource`, inside `PendingToolExecution`, then restoring with `SKIP_PRE_HOOK_BEANS = [hookSource]`.

This is not a true checkpoint-resume model. It replays any pre-hooks that already ran before confirmation, which creates two problems:

- the restore semantics are inconsistent with user expectations of "continue from where execution stopped"
- already executed pre-hooks may run twice, which is unsafe for hooks with side effects or hooks that are not strictly idempotent

## 2. Goal

Change `TOOL_CONFIRMATION` restore semantics from "re-run the pre-hook chain and skip only the confirmation source hook" to "resume from the confirmation checkpoint by skipping all pre-hooks that already completed before suspension".

When the user edits `updated_args` during approval:

- already executed pre-hooks stay skipped
- remaining pre-hooks that did not run before suspension continue execution
- those remaining pre-hooks see the merged arguments: `resolvedArguments + updated_args`

## 3. Non-Goals

- changing post-hook semantics
- adding generic checkpoint-resume support for `BLOCK`
- adding hook retry diagnostics or hook execution tracing UI
- introducing persisted hook output snapshots beyond resolved arguments

## 4. Chosen Approach

Persist the full list of completed pre-hook beans inside `PendingToolExecution` and make that list the only restore-time skip source.

Remove `hookSource` entirely.

This makes restore behavior explicit and durable:

- pre-hooks that already completed before suspension are skipped on resume
- pre-hooks that were never reached before suspension still run on resume
- the real tool and all post-hooks still execute normally after approval

## 5. Data Model Changes

### 5.1 `PendingToolExecution`

Remove:

- `hookSource`

Add:

- `executedPreHookBeans: List<String>`

Meaning:

- ordered list of pre-hook bean names that already completed for the suspended tool call
- the list includes the confirmation hook itself when suspension is caused by `REQUEST_CONFIRMATION`

### 5.2 `PreToolCallHookResult`

Add:

- `executedHookBeans: List<String>`

Meaning:

- the runtime-visible progress of the pre-hook chain at the moment the result is returned

This field is required so `DefaultAgentHookRuntime` can return hook progress back to `CustomToolCallingManager` without leaking internal loop state through global mutable context.

## 6. Runtime Semantics

### 6.1 Normal pre-hook execution

`DefaultAgentHookRuntime.runPreHooks()` maintains a local ordered list: `executedHookBeans`.

For each matching pre-hook bean:

- if outcome is `PROCEED`
  - apply `updatedArgs` if present
  - append the current bean name to `executedHookBeans`
  - continue to the next hook
- if outcome is `REQUEST_CONFIRMATION`
  - return a result that carries:
    - current `updatedArgs` or the current arguments snapshot
    - the confirmation spec
    - `executedHookBeans + current bean name`
  - stop evaluating later hooks
- if outcome is `BLOCK`
  - return immediately
  - no resume path is introduced for block behavior in this change

### 6.2 Suspension

`CustomToolCallingManager.suspendForConfirmation()` persists the full pre-hook progress into `PendingToolExecution.executedPreHookBeans`.

No single-hook fallback source remains after this change.

### 6.3 Resume after approval

`HumanInLoopResumer.resumeToolConfirmation()` performs these steps:

1. Start from `pendingExecution.resolvedArguments`.
2. Merge editable user overrides from `updated_args`.
3. Reconstruct a tool execution prompt with `SKIP_PRE_HOOK_BEANS = executedPreHookBeans`.
4. Re-enter the standard tool execution pipeline.

Expected result:

- previously completed pre-hooks are skipped
- previously unexecuted pre-hooks continue to run
- those remaining hooks evaluate the merged arguments after user edits
- the tool executes once
- post-hooks execute once

### 6.4 Resume after denial

Denied confirmations are unchanged:

- append a synthetic tool response indicating cancellation
- clear pending confirmation state
- do not execute the tool

## 7. Behavioral Example

Pre-hook order:

1. `normalizeArgsHook`
2. `policyCheckHook`
3. `toolConfirmHook`
4. `auditEnrichHook`

Initial execution:

- `normalizeArgsHook` runs and updates arguments
- `policyCheckHook` runs
- `toolConfirmHook` returns `REQUEST_CONFIRMATION`
- runtime persists `executedPreHookBeans = ["normalizeArgsHook", "policyCheckHook", "toolConfirmHook"]`
- `auditEnrichHook` has not run yet

Resume after approval with `updated_args.room = "B2001"`:

- merged arguments are built from persisted `resolvedArguments` plus the edited room field
- `normalizeArgsHook`, `policyCheckHook`, and `toolConfirmHook` are skipped
- `auditEnrichHook` runs using the merged arguments
- the tool executes
- post-hooks execute

This is the intended checkpoint-resume behavior.

## 8. Testing Requirements

### 8.1 Runtime tests

Add or update tests to verify:

- confirmation results include the complete executed pre-hook list, not only the confirmation hook
- resume passes the full executed pre-hook list through `SKIP_PRE_HOOK_BEANS`
- already executed hooks do not run again after approval
- later hooks that did not run before suspension do run after approval
- later hooks receive arguments merged from persisted resolved arguments plus user edits

### 8.2 Persistence tests

Add or update tests to verify:

- `PendingToolExecution.executedPreHookBeans` survives `SuperAgentContext -> SessionRuntimeSnapshot -> SuperAgentContext`
- no restore logic depends on `hookSource`

## 9. Compatibility Boundary

This change intentionally removes `hookSource` as a restore input.

Code paths that currently read or write `hookSource` must be updated in the same change set so there is only one restore rule in the codebase.

Historical suspended session data does not need compatibility handling in this change.

The implementation may assume a coordinated in-repo refactor where the new restore model is the only supported model after rollout.

## 10. Risks and Mitigations

Risk: a previously executed pre-hook may have produced argument mutations that later hooks depend on.

Mitigation:

- persist `resolvedArguments` exactly as they exist at suspension time
- merge user edits only after loading those persisted arguments

Risk: a hook author may expect earlier pre-hooks to re-run after user edits.

Mitigation:

- document the new contract clearly: confirmation restore is checkpoint-resume, not full pre-hook replay
- later pre-hooks must be written to rely on the merged argument snapshot they receive at resume time

## 11. Acceptance Criteria

This design is complete when:

- `hookSource` is removed from the restore model
- `executedPreHookBeans` is persisted in `PendingToolExecution`
- resume skips all already completed pre-hooks
- remaining pre-hooks continue after approval
- remaining pre-hooks see `resolvedArguments + updated_args`
- post-hooks still run once after actual tool execution
