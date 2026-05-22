# Agent Definition Loader Refactor Design

## Background

`AgentWorkspaceService` currently combines multiple responsibility domains:

- loading and merging agent runtime definition
- reading prompt and rules resources from `classpath:agents/<agentKey>/`
- parsing workspace `config.yml`
- proxying MCP definition lookup

This creates an unstable boundary. Changes in agent definition rules, workspace schema, prompt fallback, and MCP lookup all force changes in one class. The same area also mixes `Provider`, `Service`, and `Loader` concepts, which makes the dependency graph harder to reason about.

## Goal

Replace `AgentWorkspaceService` with loader abstractions split by definition domain:

- `IAgentDefinitionLoader`
- `IMcpDefinitionLoader`
- `ISkillDefinitionLoader`

The refactor should preserve current runtime behavior while making each definition source explicit and isolated.

## Non-Goals

- changing agent runtime behavior beyond the agreed stricter error handling for invalid workspace config
- redesigning tool caching in `GlobalToolRegistry`
- adding database-backed or remote definition sources
- introducing a shared top-level loader parent interface

## Design Summary

The new design separates three definition domains:

1. agent definition loading
2. MCP definition loading
3. Skill definition loading

Each domain exposes its own interface and one concrete loader implementation whose class name reflects the resource source.

## Interfaces and Implementations

### Agent Definition

Interface:

```java
public interface IAgentDefinitionLoader {
    AgentDefinition load(String agentKey);
}
```

Implementation:

```java
public class AgentDefinitionClasspathYmlLoader implements IAgentDefinitionLoader
```

Responsibility:

- read base agent configuration from `ApexGlobalProperties.agents`
- resolve workspace path from `AgentConfig.workspace` or fallback `classpath:agents/<agentKey>/`
- read and parse `classpath:agents/<agentKey>/config.yml`
- read prompt and rules resources from workspace
- apply fallback prompt chain
- merge global and workspace values
- return the final `AgentDefinition`
- cache `AgentDefinition` by `agentKey`

### MCP Definition

Interface:

```java
public interface IMcpDefinitionLoader {
    McpServerConfig load(String mcpKey);
}
```

Implementation:

```java
public class McpDefinitionYmlLoader implements IMcpDefinitionLoader
```

Responsibility:

- read `mcpKey -> McpServerConfig` from `ApexGlobalProperties.mcps`

### Skill Definition

Interface:

```java
public interface ISkillDefinitionLoader {
    SkillConfig load(String skillKey);
}
```

Implementation:

```java
public class SkillDefinitionYmlLoader implements ISkillDefinitionLoader
```

Responsibility:

- read `skillKey -> SkillConfig` from `ApexGlobalProperties.skills`

## Core Models

### Final Agent Definition

`IAgentDefinitionLoader` returns a final aggregated definition instead of exposing many fine-grained getters.

```java
public record AgentDefinition(
        String agentKey,
        ModeEnum defaultExecutionMode,
        List<String> mcpNames,
        List<String> subAgentNames,
        List<String> skillNames,
        AgentHooksConfig hooks,
        String reactPrompt,
        String planExecutorWritePlanPrompt,
        String planExecutorRunPrompt,
        String agentRules) {
}
```

This model represents the final effective runtime definition for one agent.

### Workspace Override Model

`AgentDefinitionClasspathYmlLoader` uses an internal override model for workspace data:

```java
public class AgentWorkspaceConfig {
    private List<String> allowMcps = Collections.emptyList();
    private List<String> allowSubAgents = Collections.emptyList();
    private List<String> allowSkills = Collections.emptyList();
    private ModeEnum defaultExecutionMode;
    private boolean hooksConfigured = false;
    private AgentHooksConfig hooks = AgentHooksConfig.empty();
}
```

This model is intentionally not exposed outside the agent definition domain.

## Merge Rules

`AgentDefinitionClasspathYmlLoader` must preserve current merge behavior.

### Definition Fields

- `defaultExecutionMode`
  - workspace `default-execution-mode` wins
  - otherwise use global `AgentConfig.defaultExecutionMode`
  - if both are absent, fail

- `mcpNames`
  - non-empty workspace `allow-mcps` wins
  - otherwise use global `AgentConfig.mcps`
  - otherwise use empty list

- `subAgentNames`
  - non-empty workspace `allow-sub-agents` wins
  - otherwise use global `AgentConfig.subAgents`
  - otherwise use empty list

- `skillNames`
  - non-empty workspace `allow-skills` wins
  - otherwise use global `AgentConfig.skills`
  - otherwise use empty list

- `hooks`
  - if workspace declares `hooks`, use the workspace result exactly
  - if workspace does not declare `hooks`, fallback to global `AgentConfig.hooks`
  - if workspace declares `hooks: []`, disable all hooks
  - if neither side defines hooks, use `AgentHooksConfig.empty()`

### Prompt and Rules Files

- `reactPrompt`
  - workspace `REACT_PROMPT.md`
  - fallback `classpath:agents/defaults/REACT_PROMPT.md`
  - fallback `StageSystemPrompt.getReActPrompt()`

- `planExecutorWritePlanPrompt`
  - workspace file
  - fallback defaults file
  - fallback `StageSystemPrompt.getPlanExecutorWritePlanPrompt()`

- `planExecutorRunPrompt`
  - workspace file
  - fallback defaults file
  - fallback `StageSystemPrompt.getPlanExecutorRunPrompt()`

- `agentRules`
  - workspace `AGENT.md`
  - fallback empty string

## Error Handling

The new loader behavior should be strict for broken definition data and tolerant for missing optional resources.

Fail:

- `agentKey` not found in global `agents`
- invalid workspace `config.yml`
- invalid `default-execution-mode`
- missing final `defaultExecutionMode` after merge

Allowed fallback:

- missing workspace `config.yml`
- missing workspace prompt files
- missing workspace `AGENT.md`
- missing defaults prompt files when `StageSystemPrompt` fallback exists

`AgentDefinitionClasspathYmlLoader` does not validate whether referenced MCP keys or Skill keys actually exist. That validation belongs to MCP and Skill definition consumers, not agent definition loading.

## Package Layout

Recommended new packages:

- `org.gemo.apex.definition.agent`
- `org.gemo.apex.definition.mcp`
- `org.gemo.apex.definition.skill`

Recommended files:

- `org/gemo/apex/definition/agent/IAgentDefinitionLoader.java`
- `org/gemo/apex/definition/agent/AgentDefinition.java`
- `org/gemo/apex/definition/agent/AgentWorkspaceConfig.java`
- `org/gemo/apex/definition/agent/AgentDefinitionClasspathYmlLoader.java`
- `org/gemo/apex/definition/mcp/IMcpDefinitionLoader.java`
- `org/gemo/apex/definition/mcp/McpDefinitionYmlLoader.java`
- `org/gemo/apex/definition/skill/ISkillDefinitionLoader.java`
- `org/gemo/apex/definition/skill/SkillDefinitionYmlLoader.java`

## Dependency Changes

`ApexGlobalProperties` becomes a plain configuration bean and should no longer implement:

- `AgentConfigProvider`
- `McpConfigProvider`
- `SkillConfigProvider`

These provider interfaces should be deleted as part of the refactor.

Concrete dependency direction after the refactor:

- `AgentDefinitionClasspathYmlLoader`
  - depends on `ApexGlobalProperties`
  - depends on `ResourceLoader`

- `McpDefinitionYmlLoader`
  - depends on `ApexGlobalProperties`

- `SkillDefinitionYmlLoader`
  - depends on `ApexGlobalProperties`

## Calling Site Migration

### `SuperAgentFactory`

Replace `AgentWorkspaceService` usage with `IAgentDefinitionLoader`.

Expected usage:

```java
AgentDefinition definition = agentDefinitionLoader.load(agentKey);
```

Use:

- `definition.defaultExecutionMode()`
- `definition.mcpNames()`
- `definition.subAgentNames()`
- `definition.skillNames()`

### `StagePromptBuilder`

Load `AgentDefinition` and read:

- `definition.reactPrompt()`
- `definition.planExecutorWritePlanPrompt()`
- `definition.planExecutorRunPrompt()`
- `definition.agentRules()`

### `DefaultAgentHookRuntime`

Load `AgentDefinition` and read:

- `definition.hooks()`

### `GlobalToolRegistry`

Replace mixed agent workspace and provider lookups with:

- `IAgentDefinitionLoader`
- `IMcpDefinitionLoader`
- `ISkillDefinitionLoader`

Expected flow:

1. load `AgentDefinition`
2. iterate `definition.mcpNames()`
3. load `McpServerConfig` for each key
4. iterate `definition.skillNames()`
5. load `SkillConfig` for each key

### `SubAgentToolCallbackProvider`

If only base agent metadata is needed, depend directly on `ApexGlobalProperties` rather than on `IAgentDefinitionLoader`. This avoids mixing base metadata lookup with final runtime definition loading.

### `ChatController`

If it only validates agent existence or accesses base metadata, migrate it away from provider interfaces to direct `ApexGlobalProperties` access or a narrow dedicated lookup abstraction.

## Removed Components

Delete:

- `AgentWorkspaceService`
- `AgentConfigProvider`
- `McpConfigProvider`
- `SkillConfigProvider`

## Migration Sequence

1. add new definition packages and interfaces
2. implement `AgentDefinitionClasspathYmlLoader`
3. implement `McpDefinitionYmlLoader`
4. implement `SkillDefinitionYmlLoader`
5. migrate `GlobalToolRegistry`
6. migrate `SuperAgentFactory`
7. migrate `StagePromptBuilder`
8. migrate `DefaultAgentHookRuntime`
9. migrate `SubAgentToolCallbackProvider`
10. migrate `ChatController`
11. remove `AgentWorkspaceService`
12. remove provider interfaces
13. simplify `ApexGlobalProperties`

## Testing Strategy

### Unit Tests

Add:

- `AgentDefinitionClasspathYmlLoaderTest`
- `McpDefinitionYmlLoaderTest`
- `SkillDefinitionYmlLoaderTest`

`AgentDefinitionClasspathYmlLoaderTest` must cover:

- workspace missing -> global fallback
- workspace `allow-mcps` override
- workspace `allow-sub-agents` override
- workspace `allow-skills` override
- workspace hooks override
- `hooks: []` disables all hooks
- workspace hooks absent -> global hooks fallback
- workspace execution mode override
- invalid execution mode fails
- prompt fallback chain
- `AGENT.md` missing -> empty string
- missing agent fails
- invalid `config.yml` fails
- repeated `load(agentKey)` is stable under cache

### Regression Tests

Update or add targeted tests for:

- `GlobalToolRegistry`
- `StagePromptBuilder`

The objective is to prove behavior parity after the dependency shift.

## Acceptance Criteria

The refactor is complete when:

- all `AgentWorkspaceService` references are removed
- all provider interface references are removed
- `ApexGlobalProperties` is a plain configuration bean
- agent runtime behavior matches pre-refactor behavior for prompts, rules, hooks, execution mode, MCP list, Skill list, and SubAgent list
- definition responsibilities are isolated by domain
- unit and regression tests pass
