package org.gemo.apex.core.definition;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gemo.apex.common.agent.AgentDefinition;
import org.gemo.apex.common.agent.DefinitionSchemaVersion;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.*;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.core.agent.AgentPorts;
import org.gemo.apex.core.exception.InvalidAgentDefinitionException;
import org.gemo.apex.core.lifecycle.ToolBindingMatcher;
import org.gemo.apex.core.tool.ToolCatalog;
import org.gemo.apex.extension.hook.LifecycleHook;

public final class AgentDefinitionValidator {
    private final ToolBindingMatcher toolMatcher = new ToolBindingMatcher();
    private static final Map<HookPoint, Class<? extends HookContextView>> CONTEXT_TYPES =
            Map.ofEntries(
                    Map.entry(HookPoint.AGENT_BUILD, AgentBuildContext.class),
                    Map.entry(HookPoint.TURN_START, TurnStartContext.class),
                    Map.entry(HookPoint.ITERATION_START, IterationStartContext.class),
                    Map.entry(
                            HookPoint.PRE_MESSAGE_COMPRESSION, PreMessageCompressionContext.class),
                    Map.entry(
                            HookPoint.POST_MESSAGE_COMPRESSION,
                            PostMessageCompressionContext.class),
                    Map.entry(HookPoint.PRE_MODEL_CALL, PreModelCallContext.class),
                    Map.entry(HookPoint.POST_MODEL_CALL, PostModelCallContext.class),
                    Map.entry(HookPoint.PRE_TOOL_CALL, PreToolCallContext.class),
                    Map.entry(HookPoint.POST_TOOL_CALL, PostToolCallContext.class),
                    Map.entry(HookPoint.ITERATION_END, IterationEndContext.class),
                    Map.entry(HookPoint.TURN_END, TurnEndContext.class));
    private static final Map<HookPoint, Class<? extends LifecycleHookResult>> RESULT_TYPES =
            Map.ofEntries(
                    Map.entry(HookPoint.AGENT_BUILD, AgentBuildHookResult.class),
                    Map.entry(HookPoint.TURN_START, LoopHookResult.class),
                    Map.entry(HookPoint.ITERATION_START, LoopHookResult.class),
                    Map.entry(
                            HookPoint.PRE_MESSAGE_COMPRESSION,
                            PreMessageCompressionHookResult.class),
                    Map.entry(
                            HookPoint.POST_MESSAGE_COMPRESSION,
                            PostMessageCompressionHookResult.class),
                    Map.entry(HookPoint.PRE_MODEL_CALL, PreModelCallHookResult.class),
                    Map.entry(HookPoint.POST_MODEL_CALL, PostModelCallHookResult.class),
                    Map.entry(HookPoint.PRE_TOOL_CALL, PreToolCallHookResult.class),
                    Map.entry(HookPoint.POST_TOOL_CALL, PostToolCallHookResult.class),
                    Map.entry(HookPoint.ITERATION_END, LoopHookResult.class),
                    Map.entry(HookPoint.TURN_END, TurnEndHookResult.class));

    public void structuralPrecheck(AgentDefinition definition, AgentPorts ports) {
        if (!DefinitionSchemaVersion.V1.equals(definition.schemaVersion())) {
            throw new InvalidAgentDefinitionException("仅支持定义版本 " + DefinitionSchemaVersion.V1);
        }
        validateBindings(definition, ports, Set.of(HookPoint.AGENT_BUILD));
    }

    public void validate(AgentDefinition definition, ToolCatalog catalog, AgentPorts ports) {
        if (definition.prompt().systemPrompt().isBlank()) {
            throw new InvalidAgentDefinitionException("prompt.systemPrompt 不能为空");
        }
        if (!catalog.ordered().stream()
                .map(tool -> tool.definition().name())
                .collect(Collectors.toSet())
                .containsAll(definition.tools().availableTools())) {
            throw new InvalidAgentDefinitionException("availableTools 必须全部可解析");
        }
        Set<String> skillNames =
                ports.skillProvider().loadSkills().stream()
                        .map(skill -> skill.name())
                        .collect(Collectors.toSet());
        if (!skillNames.containsAll(definition.enabledSkills())) {
            throw new InvalidAgentDefinitionException("enabledSkills 存在无法解析的名称");
        }
        validateBindings(definition, ports, definition.hooks().keySet());
    }

    public void validateRecoveryBindings(AgentDefinition definition, AgentPorts ports) {
        validateBindings(definition, ports, definition.hooks().keySet());
    }

    private void validateBindings(
            AgentDefinition definition, AgentPorts ports, Set<HookPoint> points) {
        for (HookPoint point : points) {
            Set<String> ids = new HashSet<>();
            for (HookBinding binding : definition.hooks().getOrDefault(point, List.of())) {
                if (!ids.add(binding.id())) {
                    throw new InvalidAgentDefinitionException(
                            point + " Hook ID 重复: " + binding.id());
                }
                if (binding.tools().stream()
                        .anyMatch(
                                pattern ->
                                        definition.tools().availableTools().stream()
                                                .noneMatch(
                                                        toolName ->
                                                                toolMatcher.matches(
                                                                        pattern, toolName)))) {
                    throw new InvalidAgentDefinitionException(
                            "Hook 工具匹配超出 availableTools: " + binding.id());
                }
                LifecycleHook<?, ?> hook = ports.hookResolver().resolve(point, binding.hook());
                if (hook == null
                        || hook.descriptor() == null
                        || hook.descriptor().hookPoint() != point
                        || hook.descriptor().contextType() != CONTEXT_TYPES.get(point)
                        || hook.descriptor().resultType() != RESULT_TYPES.get(point)) {
                    throw new InvalidAgentDefinitionException(
                            "Hook descriptor 不匹配: " + binding.id());
                }
            }
        }
    }
}
