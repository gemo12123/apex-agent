package org.gemo.apex.core.definition;

import java.util.*;
import java.util.stream.Collectors;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.AgentBuildContext;
import org.gemo.apex.common.hook.result.ContinueAgentBuild;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.snapshot.HistoricalToolBinding;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.common.tool.ToolAvailabilitySnapshot;
import org.gemo.apex.common.tool.ToolOrigin;
import org.gemo.apex.common.tool.UnavailableToolSource;
import org.gemo.apex.core.agent.AgentAssemblyResult;
import org.gemo.apex.core.agent.AgentPorts;
import org.gemo.apex.core.exception.HookContractException;
import org.gemo.apex.core.exception.InvalidAgentDefinitionException;
import org.gemo.apex.core.exception.UnavailableToolBindingException;
import org.gemo.apex.core.lifecycle.ToolBindingMatcher;
import org.gemo.apex.core.tool.ToolCatalog;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.tool.AgentTool;

public final class AgentDefinitionAssembler {
    private static final System.Logger LOG =
            System.getLogger(AgentDefinitionAssembler.class.getName());
    private final AgentDefinitionValidator validator = new AgentDefinitionValidator();
    private final ToolBindingMatcher toolMatcher = new ToolBindingMatcher();

    public AgentAssemblyResult assemble(
            String sessionId,
            String agentKey,
            Optional<SessionSnapshot> existingSession,
            AgentPorts ports,
            SharedDataStore sharedData) {
        AgentDefinition source = ports.definitionProvider().load(agentKey);
        if (source == null) {
            throw new InvalidAgentDefinitionException("找不到 Agent 定义: " + agentKey);
        }
        if (!source.metadata().agentKey().equals(agentKey)) {
            throw new InvalidAgentDefinitionException("metadata.agentKey 与请求不一致");
        }
        validator.structuralPrecheck(source, ports);
        AgentDefinitionDraft draft = new AgentDefinitionDraft(source);
        dispatchAgentBuild(sessionId, source, draft, ports, sharedData);
        AgentDefinition candidate = materialize(source, draft);
        ToolCatalog loadedCatalog = new ToolCatalog(ports.toolProvider().loadTools(candidate));
        Classification classification =
                classifyUnavailable(
                        candidate,
                        loadedCatalog,
                        ports.toolAvailabilityProvider().current(),
                        existingSession,
                        ports);
        validator.validate(classification.definition(), classification.catalog(), ports);
        Set<String> enabled =
                existingSession
                        .map(SessionSnapshot::enabledTools)
                        .orElse(classification.definition().tools().defaultEnabledTools());
        if (existingSession.isPresent()) {
            Set<String> retained = new LinkedHashSet<>(enabled);
            Set<String> historicalNames =
                    classification.history().stream()
                            .map(HistoricalToolBinding::toolName)
                            .collect(Collectors.toSet());
            retained.removeIf(
                    name ->
                            !classification.definition().tools().availableTools().contains(name)
                                    && historicalNames.contains(name));
            enabled = Set.copyOf(retained);
        }
        if (!classification.definition().tools().availableTools().containsAll(enabled)) {
            throw new InvalidAgentDefinitionException("session enabledTools 出现普通配置漂移");
        }
        return new AgentAssemblyResult(
                new AgentDefinitionSnapshot(
                        classification.definition(), draft.prefixDeveloperMessages()),
                enabled,
                classification.history(),
                classification.catalog());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dispatchAgentBuild(
            String sessionId,
            AgentDefinition source,
            AgentDefinitionDraft draft,
            AgentPorts ports,
            SharedDataStore sharedData) {
        List<HookBinding> snapshot =
                source.hooks().getOrDefault(HookPoint.AGENT_BUILD, List.of()).stream()
                        .filter(HookBinding::enabled)
                        .sorted(
                                Comparator.comparingInt(HookBinding::order)
                                        .thenComparing(HookBinding::id))
                        .toList();
        for (HookBinding binding : snapshot) {
            LifecycleHook hook =
                    ports.hookResolver().resolve(HookPoint.AGENT_BUILD, binding.hook());
            try {
                Object raw =
                        hook.apply(
                                new AgentBuildContext(
                                        sessionId,
                                        binding,
                                        new AgentDefinitionSnapshot(
                                                materialize(source, draft),
                                                draft.prefixDeveloperMessages()),
                                        sharedData));
                if (!(raw instanceof ContinueAgentBuild result)) {
                    throw new HookContractException("AGENT_BUILD 返回了非法结果: " + binding.id());
                }
                AgentDefinitionDraft temporary = draft.copy();
                result.operations().forEach(temporary::apply);
                materialize(source, temporary);
                result.operations().forEach(draft::apply);
            } catch (HookContractException error) {
                throw error;
            } catch (RuntimeException error) {
                LOG.log(
                        System.Logger.Level.WARNING,
                        "AGENT_BUILD Hook 执行失败，已跳过: " + binding.id(),
                        error);
            }
        }
    }

    private AgentDefinition materialize(AgentDefinition source, AgentDefinitionDraft draft) {
        Set<String> defaults = new LinkedHashSet<>(source.tools().defaultEnabledTools());
        defaults.retainAll(draft.availableTools());
        return new AgentDefinition(
                source.schemaVersion(),
                source.metadata(),
                draft.prompt(),
                source.messageCompression(),
                new ToolSetDefinition(draft.availableTools(), defaults),
                source.enabledSkills(),
                source.subAgents(),
                draft.hooks());
    }

    private Classification classifyUnavailable(
            AgentDefinition candidate,
            ToolCatalog catalog,
            ToolAvailabilitySnapshot availability,
            Optional<SessionSnapshot> existingSession,
            AgentPorts ports) {
        Set<String> effective = new LinkedHashSet<>(candidate.tools().availableTools());
        List<HistoricalToolBinding> history =
                new ArrayList<>(
                        existingSession
                                .map(SessionSnapshot::historicalToolBindings)
                                .orElse(List.of()));
        for (String name : candidate.tools().availableTools()) {
            if (catalog.contains(name)) {
                continue;
            }
            UnavailableToolSource source =
                    availability.unavailableSources().stream()
                            .filter(item -> name.startsWith(item.stableNamePrefix()))
                            .findFirst()
                            .orElse(null);
            boolean unavailable =
                    availability.unavailableToolNames().contains(name) || source != null;
            boolean existed =
                    existingSession
                            .map(
                                    session ->
                                            session.activeDefinition()
                                                    .availableTools()
                                                    .contains(name))
                            .orElse(false);
            if (!unavailable) {
                throw new InvalidAgentDefinitionException("工具无法解析且没有不可用来源: " + name);
            }
            if (!existed) {
                throw new UnavailableToolBindingException(name);
            }
            effective.remove(name);
            HistoricalToolBinding binding =
                    new HistoricalToolBinding(
                            name,
                            source == null ? ToolOrigin.LOCAL : source.origin(),
                            source == null ? name : source.sourceId(),
                            source == null ? "UNAVAILABLE" : source.reasonCode(),
                            ports.timeProvider().now());
            if (history.stream().noneMatch(item -> item.identity().equals(binding.identity()))) {
                history.add(binding);
            }
        }
        Set<String> defaults = new LinkedHashSet<>(candidate.tools().defaultEnabledTools());
        defaults.retainAll(effective);
        Set<String> removed = new LinkedHashSet<>(candidate.tools().availableTools());
        removed.removeAll(effective);
        AgentDefinition result =
                new AgentDefinition(
                        candidate.schemaVersion(),
                        candidate.metadata(),
                        candidate.prompt(),
                        candidate.messageCompression(),
                        new ToolSetDefinition(effective, defaults),
                        candidate.enabledSkills(),
                        candidate.subAgents(),
                        retainResolvableBindings(candidate.hooks(), effective, removed));
        List<AgentTool> effectiveTools =
                catalog.ordered().stream()
                        .filter(tool -> effective.contains(tool.definition().name()))
                        .toList();
        return new Classification(result, new ToolCatalog(effectiveTools), history);
    }

    private Map<HookPoint, List<HookBinding>> retainResolvableBindings(
            Map<HookPoint, List<HookBinding>> hooks,
            Set<String> effectiveTools,
            Set<String> removedTools) {
        Map<HookPoint, List<HookBinding>> retained = new EnumMap<>(HookPoint.class);
        hooks.forEach(
                (point, bindings) -> {
                    List<HookBinding> next =
                            bindings.stream()
                                    .map(
                                            binding ->
                                                    retainResolvablePatterns(
                                                            binding, effectiveTools, removedTools))
                                    .filter(Objects::nonNull)
                                    .toList();
                    if (!next.isEmpty()) {
                        retained.put(point, next);
                    }
                });
        return Map.copyOf(retained);
    }

    private HookBinding retainResolvablePatterns(
            HookBinding binding, Set<String> effectiveTools, Set<String> removedTools) {
        if (binding.tools().isEmpty()) {
            return binding;
        }
        List<String> retainedPatterns =
                binding.tools().stream()
                        .filter(
                                pattern ->
                                        effectiveTools.stream()
                                                        .anyMatch(
                                                                tool ->
                                                                        toolMatcher.matches(
                                                                                pattern, tool))
                                                || removedTools.stream()
                                                        .noneMatch(
                                                                tool ->
                                                                        toolMatcher.matches(
                                                                                pattern, tool)))
                        .toList();
        if (retainedPatterns.isEmpty()) {
            return null;
        }
        if (retainedPatterns.equals(binding.tools())) {
            return binding;
        }
        return new HookBinding(
                binding.id(),
                binding.hook(),
                binding.order(),
                binding.enabled(),
                retainedPatterns,
                binding.options());
    }

    private record Classification(
            AgentDefinition definition, ToolCatalog catalog, List<HistoricalToolBinding> history) {}
}
