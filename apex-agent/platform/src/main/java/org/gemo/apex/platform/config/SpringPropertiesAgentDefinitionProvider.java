package org.gemo.apex.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.springframework.core.io.ResourceLoader;

public final class SpringPropertiesAgentDefinitionProvider implements AgentDefinitionProvider {
    private final Map<String, AgentDefinition> definitions;
    private final List<AgentMetadata> metadata;

    public SpringPropertiesAgentDefinitionProvider(
            ApexAgentPlatformProperties properties, ResourceLoader resourceLoader) {
        if (properties.getDefinitionResource() != null
                && !properties.getDefinitionResource().isBlank()) {
            throw new IllegalArgumentException("Spring agents 与 definition-resource 不能同时作为定义源");
        }
        if (properties.getAgents() == null || properties.getAgents().isEmpty()) {
            throw new IllegalArgumentException("apex.platform.agents 不能为空");
        }
        Map<String, AgentDefinition> result = new LinkedHashMap<>();
        properties
                .getAgents()
                .forEach((key, value) -> result.put(key, convert(key, value, resourceLoader)));
        definitions = Map.copyOf(result);
        metadata = result.values().stream().map(AgentDefinition::metadata).toList();
    }

    @Override
    public AgentDefinition load(String agentKey) {
        AgentDefinition definition = definitions.get(agentKey);
        if (definition == null) {
            throw new IllegalArgumentException("Agent 不存在: " + agentKey);
        }
        return definition;
    }

    @Override
    public List<AgentMetadata> listAgents() {
        return metadata;
    }

    private static AgentDefinition convert(
            String key, ApexAgentPlatformProperties.Agent source, ResourceLoader loader) {
        if (source == null) {
            throw new IllegalArgumentException("Agent 配置不能为空: " + key);
        }
        String systemPrompt =
                read(source.getPrompt() == null ? null : source.getPrompt().getSystem(), loader);
        var compression = source.getMessageCompression();
        var tools = source.getTools();
        Map<String, SubAgentDefinition> subAgents = new LinkedHashMap<>();
        source.getSubAgents()
                .forEach(
                        (name, value) ->
                                subAgents.put(
                                        name,
                                        new SubAgentDefinition(
                                                value.getAgentKey(), value.getDescription())));
        Map<HookPoint, List<HookBinding>> hooks = new LinkedHashMap<>();
        source.getHooks()
                .forEach(
                        (point, bindings) ->
                                hooks.put(
                                        HookPoint.valueOf(point),
                                        bindings.stream()
                                                .map(
                                                        binding ->
                                                                new HookBinding(
                                                                        binding.getId(),
                                                                        binding.getName(),
                                                                        binding.getOrder(),
                                                                        binding.isEnabled(),
                                                                        binding.getTools(),
                                                                        binding.getOptions()))
                                                .toList()));
        return new AgentDefinition(
                DefinitionSchemaVersion.V1,
                new AgentMetadata(key, source.getName(), source.getDescription()),
                new PromptDefinition(systemPrompt, source.getPrompt().getMaxIterations()),
                new MessageCompressionDefinition(
                        compression.isEnabled(),
                        compression.getMaxMessages(),
                        compression.getTokenThreshold(),
                        compression.getCharacterHardLimit()),
                new ToolSetDefinition(tools.getAvailable(), tools.getDefaultEnabled()),
                source.getSkills(),
                subAgents,
                hooks);
    }

    private static String read(String location, ResourceLoader loader) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("prompt.system 不能为空");
        }
        try (var input = loader.getResource(location).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 prompt 资源: " + location, exception);
        }
    }
}
