package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;

public final class AgentDefinitionDraft {
    private PromptDefinition prompt;
    private final Set<String> availableTools;
    private final Map<HookPoint, List<HookBinding>> hooks;
    private final List<PrefixDeveloperMessage> prefixDeveloperMessages;

    public AgentDefinitionDraft(AgentDefinition definition) {
        nonNull(definition, "definition");
        this.prompt = definition.prompt();
        this.availableTools = new LinkedHashSet<>(definition.tools().availableTools());
        this.hooks = new EnumMap<>(HookPoint.class);
        this.prefixDeveloperMessages = new ArrayList<>();
        definition
                .hooks()
                .forEach((point, bindings) -> this.hooks.put(point, new ArrayList<>(bindings)));
    }

    private AgentDefinitionDraft(AgentDefinitionDraft source) {
        this.prompt = source.prompt;
        this.availableTools = new LinkedHashSet<>(source.availableTools);
        this.hooks = new EnumMap<>(HookPoint.class);
        source.hooks.forEach((point, bindings) -> this.hooks.put(point, new ArrayList<>(bindings)));
        this.prefixDeveloperMessages = new ArrayList<>(source.prefixDeveloperMessages);
    }

    public AgentDefinitionDraft copy() {
        return new AgentDefinitionDraft(this);
    }

    public PromptDefinition prompt() {
        return prompt;
    }

    public Set<String> availableTools() {
        return Set.copyOf(availableTools);
    }

    public Map<HookPoint, List<HookBinding>> hooks() {
        Map<HookPoint, List<HookBinding>> copy = new EnumMap<>(HookPoint.class);
        hooks.forEach((point, bindings) -> copy.put(point, List.copyOf(bindings)));
        return Map.copyOf(copy);
    }

    public List<PrefixDeveloperMessage> prefixDeveloperMessages() {
        return List.copyOf(prefixDeveloperMessages);
    }

    public void apply(AgentDefinitionOperation operation) {
        nonNull(operation, "operation");
        switch (operation) {
            case AddAvailableTool add -> availableTools.add(add.toolName());
            case RemoveAvailableTool remove -> availableTools.remove(remove.toolName());
            case ReplacePrompt replace -> prompt = replace.prompt();
            case AppendPrefixDeveloperMessage append ->
                    prefixDeveloperMessages.add(append.message());
            case AddHookBinding add ->
                    hooks.computeIfAbsent(add.hookPoint(), ignored -> new ArrayList<>())
                            .add(add.binding());
            case RemoveHookBinding remove ->
                    hooks.computeIfAbsent(remove.hookPoint(), ignored -> new ArrayList<>())
                            .removeIf(binding -> binding.id().equals(remove.bindingId()));
        }
    }
}
