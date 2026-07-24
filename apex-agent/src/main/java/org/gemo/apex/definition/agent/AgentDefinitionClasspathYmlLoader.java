package org.gemo.apex.definition.agent;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.prompt.StageSystemPrompt;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentDefinitionClasspathYmlLoader implements IAgentDefinitionLoader {

    private final ApexGlobalProperties apexGlobalProperties;
    private final ResourceLoader resourceLoader;
    private final Map<String, AgentDefinition> cache = new ConcurrentHashMap<>();

    public AgentDefinitionClasspathYmlLoader(ApexGlobalProperties apexGlobalProperties, ResourceLoader resourceLoader) {
        this.apexGlobalProperties = apexGlobalProperties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public AgentDefinition load(String agentKey) {
        return cache.computeIfAbsent(agentKey, this::doLoad);
    }

    private AgentDefinition doLoad(String agentKey) {
        AgentConfig agentConfig = requireAgentConfig(agentKey);
        String workspaceRoot = resolveWorkspaceRoot(agentKey, agentConfig);
        AgentWorkspaceConfig workspaceConfig = loadWorkspaceConfig(agentKey, workspaceRoot);
        return new AgentDefinition(
                agentKey,
                resolveExecutionMode(agentKey, agentConfig, workspaceConfig),
                resolveNames(workspaceConfig.getAllowMcps(), agentConfig.getMcps()),
                resolveNames(workspaceConfig.getAllowSubAgents(), agentConfig.getSubAgents()),
                resolveNames(workspaceConfig.getAllowSkills(), agentConfig.getSkills()),
                resolveHooks(agentConfig, workspaceConfig),
                resolvePrompt(workspaceRoot, "REACT_PROMPT.md", StageSystemPrompt.getReActPrompt()),
                resolvePrompt(
                        workspaceRoot,
                        "PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md",
                        StageSystemPrompt.getPlanExecutorWritePlanPrompt()),
                resolvePrompt(
                        workspaceRoot,
                        "PLAN_EXECUTOR_RUN_PROMPT.md",
                        StageSystemPrompt.getPlanExecutorRunPrompt()),
                readWorkspaceFile(workspaceRoot, "AGENT.md", ""));
    }

    private AgentConfig requireAgentConfig(String agentKey) {
        Map<String, AgentConfig> agents = apexGlobalProperties.getAgents();
        AgentConfig config = agents == null ? null : agents.get(agentKey);
        if (config == null) {
            throw new IllegalStateException("Unknown agent: " + agentKey);
        }
        return config;
    }

    private String resolveWorkspaceRoot(String agentKey, AgentConfig agentConfig) {
        if (agentConfig.getWorkspace() != null && !agentConfig.getWorkspace().isBlank()) {
            return agentConfig.getWorkspace();
        }
        return "classpath:agents/" + agentKey + "/";
    }

    @SuppressWarnings("unchecked")
    private AgentWorkspaceConfig loadWorkspaceConfig(String agentKey, String workspaceRoot) {
        String configContent = readWorkspaceFile(workspaceRoot, "config.yml", null);
        if (configContent == null) {
            return new AgentWorkspaceConfig();
        }
        try {
            Map<String, Object> map = new Yaml().load(configContent);
            AgentWorkspaceConfig config = new AgentWorkspaceConfig();
            if (map == null) {
                return config;
            }
            if (map.containsKey("allow-mcps")) {
                config.setAllowMcps((List<String>) map.get("allow-mcps"));
            }
            if (map.containsKey("allow-sub-agents")) {
                config.setAllowSubAgents((List<String>) map.get("allow-sub-agents"));
            }
            if (map.containsKey("allow-skills")) {
                config.setAllowSkills((List<String>) map.get("allow-skills"));
            }
            if (map.containsKey("default-execution-mode")) {
                config.setDefaultExecutionMode(parseMode(map.get("default-execution-mode"), agentKey));
            }
            if (map.containsKey("hooks")) {
                config.setHooksConfigured(true);
                config.setHooks(parseHooks(map.get("hooks")));
            }
            return config;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse workspace config for agent " + agentKey, ex);
        }
    }

    private ModeEnum resolveExecutionMode(String agentKey, AgentConfig agentConfig, AgentWorkspaceConfig workspaceConfig) {
        if (workspaceConfig.getDefaultExecutionMode() != null) {
            return workspaceConfig.getDefaultExecutionMode();
        }
        if (agentConfig.getDefaultExecutionMode() != null) {
            return agentConfig.getDefaultExecutionMode();
        }
        throw new IllegalStateException("Agent " + agentKey + " is missing required default execution mode");
    }

    private List<String> resolveNames(List<String> workspaceNames, List<String> globalNames) {
        if (workspaceNames != null && !workspaceNames.isEmpty()) {
            return List.copyOf(workspaceNames);
        }
        return globalNames == null ? List.of() : List.copyOf(globalNames);
    }

    private AgentHooksConfig resolveHooks(AgentConfig agentConfig, AgentWorkspaceConfig workspaceConfig) {
        if (workspaceConfig.isHooksConfigured()) {
            return workspaceConfig.getHooks();
        }
        return agentConfig.getHooks() != null ? agentConfig.getHooks() : AgentHooksConfig.empty();
    }

    private String resolvePrompt(String workspaceRoot, String fileName, String fallbackValue) {
        String content = readWorkspaceFile(workspaceRoot, fileName, null);
        if (content != null) {
            return content;
        }
        String defaultContent = readDefaultFile(fileName);
        return defaultContent != null ? defaultContent : fallbackValue;
    }

    private String readWorkspaceFile(String workspaceRoot, String fileName, String fallbackValue) {
        String content = readResourceFile(workspaceRoot + fileName);
        return content != null ? content : fallbackValue;
    }

    private String readDefaultFile(String fileName) {
        return readResourceFile("classpath:agents/defaults/" + fileName);
    }

    private String readResourceFile(String resourcePath) {
        try {
            Resource resource = resourceLoader.getResource(resourcePath);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read resource " + resourcePath, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentHooksConfig parseHooks(Object rawHooks) {
        if (rawHooks instanceof List<?> hooksList && hooksList.isEmpty()) {
            return AgentHooksConfig.disabled();
        }
        if (!(rawHooks instanceof Map<?, ?> hookMap)) {
            return AgentHooksConfig.empty();
        }
        return AgentHooksConfig.builder()
                .turnStart(parseHookBindings(hookMap.get("turn-start")))
                .traceStart(parseHookBindings(hookMap.get("trace-start")))
                .preModelCall(parseHookBindings(hookMap.get("pre-model-call")))
                .postModelCall(parseHookBindings(hookMap.get("post-model-call")))
                .preToolCall(parseHookBindings(hookMap.get("pre-tool-call")))
                .postToolCall(parseHookBindings(hookMap.get("post-tool-call")))
                .traceEnd(parseHookBindings(hookMap.get("trace-end")))
                .turnEnd(parseHookBindings(hookMap.get("turn-end")))
                .disabled(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<HookBindingConfig> parseHookBindings(Object rawBindings) {
        if (!(rawBindings instanceof List<?> bindings)) {
            return Collections.emptyList();
        }
        return bindings.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::parseHookBinding)
                .collect(Collectors.toList());
    }

    private HookBindingConfig parseHookBinding(Map<?, ?> rawBinding) {
        Object enabled = rawBinding.get("enabled");
        Object order = rawBinding.get("order");
        Object tools = rawBinding.get("tools");
        Object options = rawBinding.get("options");
        return HookBindingConfig.builder()
                .bean(rawBinding.get("bean") == null ? null : String.valueOf(rawBinding.get("bean")))
                .enabled(enabled == null || Boolean.parseBoolean(String.valueOf(enabled)))
                .order(order instanceof Number number ? number.intValue() : parseInt(order))
                .tools(tools instanceof List<?> toolList
                        ? toolList.stream().map(String::valueOf).toList()
                        : List.of("*"))
                .options(options instanceof Map<?, ?> optionMap
                        ? optionMap.entrySet().stream().collect(Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()),
                                Map.Entry::getValue))
                        : Map.of())
                .build();
    }

    private int parseInt(Object rawValue) {
        if (rawValue == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(rawValue).trim());
    }

    private ModeEnum parseMode(Object rawValue, String agentKey) {
        String value = String.valueOf(rawValue).trim();
        try {
            return ModeEnum.fromLlmValue(value);
        } catch (Exception first) {
            try {
                return ModeEnum.valueOf(value.replace('-', '_').toUpperCase());
            } catch (Exception second) {
                throw new IllegalArgumentException(
                        "Invalid default-execution-mode for agent " + agentKey + ": " + value,
                        second);
            }
        }
    }
}
