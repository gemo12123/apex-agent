package org.gemo.apex.common.agent;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.*;

public record AgentDefinition(String schemaVersion, AgentMetadata metadata, PromptDefinition prompt,
                              MessageCompressionDefinition messageCompression, ToolSetDefinition tools,
                              Set<String> enabledSkills, Map<String, SubAgentDefinition> subAgents,
                              Map<HookPoint, List<HookBinding>> hooks) {
    public AgentDefinition {
        schemaVersion = required(schemaVersion, "schemaVersion");
        if (!DefinitionSchemaVersion.V1.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion 必须为 " + DefinitionSchemaVersion.V1);
        }
        metadata = nonNull(metadata, "metadata");
        prompt = nonNull(prompt, "prompt");
        messageCompression = nonNull(messageCompression, "messageCompression");
        tools = nonNull(tools, "tools");
        enabledSkills = immutableNames(enabledSkills, "enabledSkills");
        nonNull(subAgents, "subAgents");
        subAgents = Collections.unmodifiableMap(new LinkedHashMap<>(subAgents));
        nonNull(hooks, "hooks");
        Map<HookPoint, List<HookBinding>> hookCopy = new LinkedHashMap<>();
        hooks.forEach((point, bindings) -> hookCopy.put(nonNull(point, "hooks key"), List.copyOf(bindings)));
        hooks = Collections.unmodifiableMap(hookCopy);
    }
}
