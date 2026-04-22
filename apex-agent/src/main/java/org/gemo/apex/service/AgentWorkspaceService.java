package org.gemo.apex.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.config.model.McpServerConfig;
import org.gemo.apex.config.provider.AgentConfigProvider;
import org.gemo.apex.config.provider.McpConfigProvider;
import org.gemo.apex.config.provider.SkillConfigProvider;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.prompt.StageSystemPrompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
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
@Service
public class AgentWorkspaceService {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private AgentConfigProvider agentConfigProvider;

    @Autowired
    private McpConfigProvider mcpConfigProvider;

    @Autowired
    private SkillConfigProvider skillConfigProvider;

    private final Map<String, WorkspaceConfig> configCache = new ConcurrentHashMap<>();

    public String getPlanExecutorWritePlanPrompt(String agentKey) {
        String content = readWorkspaceFile(agentKey, "PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md");
        if (content == null) {
            content = readDefaultFile("PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md");
        }
        return content != null ? content : StageSystemPrompt.getPlanExecutorWritePlanPrompt();
    }

    public String getPlanExecutorRunPrompt(String agentKey) {
        String content = readWorkspaceFile(agentKey, "PLAN_EXECUTOR_RUN_PROMPT.md");
        if (content == null) {
            content = readDefaultFile("PLAN_EXECUTOR_RUN_PROMPT.md");
        }
        return content != null ? content : StageSystemPrompt.getPlanExecutorRunPrompt();
    }

    public String getReActPrompt(String agentKey) {
        String content = readWorkspaceFile(agentKey, "REACT_PROMPT.md");
        if (content == null) {
            content = readDefaultFile("REACT_PROMPT.md");
        }
        return content != null ? content : StageSystemPrompt.getReActPrompt();
    }

    public String getAgentRules(String agentKey) {
        String content = readWorkspaceFile(agentKey, "AGENT.md");
        return content != null ? content : "";
    }

    public String getSkillsDirectory(String agentKey) {
        String workspacePath = getWorkspacePath(agentKey);
        if (workspacePath == null) {
            return null;
        }

        try {
            Resource skillsDir = resourceLoader.getResource(workspacePath + "skills/");
            if (skillsDir.exists()) {
                return skillsDir.getFile().getAbsolutePath();
            }
        } catch (IOException e) {
            log.debug("Skills directory does not exist: {}/skills/", workspacePath);
        }
        return null;
    }

    public WorkspaceConfig getWorkspaceConfig(String agentKey) {
        return configCache.computeIfAbsent(agentKey, this::loadWorkspaceConfig);
    }

    public ModeEnum getDefaultExecutionMode(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getDefaultExecutionMode() != null) {
            return config.getDefaultExecutionMode();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        if (agentConfig != null && agentConfig.getDefaultExecutionMode() != null) {
            return agentConfig.getDefaultExecutionMode();
        }
        return null;
    }

    public List<String> getMcpNames(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getAllowMcps() != null && !config.getAllowMcps().isEmpty()) {
            return config.getAllowMcps();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        return agentConfig != null && agentConfig.getMcps() != null ? agentConfig.getMcps() : Collections.emptyList();
    }

    public List<String> getSubAgents(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getAllowSubAgents() != null && !config.getAllowSubAgents().isEmpty()) {
            return config.getAllowSubAgents();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        return agentConfig != null && agentConfig.getSubAgents() != null ? agentConfig.getSubAgents()
                : Collections.emptyList();
    }

    public List<String> getSkills(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getAllowSkills() != null && !config.getAllowSkills().isEmpty()) {
            return config.getAllowSkills();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        return agentConfig != null && agentConfig.getSkills() != null ? agentConfig.getSkills()
                : Collections.emptyList();
    }

    public AgentHooksConfig getHooks(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.isHooksConfigured()) {
            return config.getHooks();
        }

        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        return agentConfig != null && agentConfig.getHooks() != null
                ? agentConfig.getHooks()
                : AgentHooksConfig.empty();
    }

    public McpServerConfig getMcpServerConfig(String mcpKey) {
        return mcpConfigProvider.getMcpConfig(mcpKey);
    }

    private String getWorkspacePath(String agentKey) {
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        if (agentConfig != null && agentConfig.getWorkspace() != null) {
            return agentConfig.getWorkspace();
        }
        return "classpath:agents/" + agentKey + "/";
    }

    private String readWorkspaceFile(String agentKey, String fileName) {
        String workspacePath = getWorkspacePath(agentKey);
        if (workspacePath == null) {
            return null;
        }
        return readResourceFile(workspacePath + fileName);
    }

    private String readDefaultFile(String fileName) {
        return readResourceFile("classpath:agents/defaults/" + fileName);
    }

    private String readResourceFile(String resourcePath) {
        try {
            Resource resource = resourceLoader.getResource(resourcePath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    log.debug("Read workspace resource: {}", resourcePath);
                    return content;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read workspace resource {}: {}", resourcePath, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private WorkspaceConfig loadWorkspaceConfig(String agentKey) {
        String configContent = readWorkspaceFile(agentKey, "config.yml");
        if (configContent == null) {
            log.info("Agent {} has no config.yml, using empty workspace config", agentKey);
            return new WorkspaceConfig();
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(configContent);
            WorkspaceConfig config = new WorkspaceConfig();
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

            log.info(
                    "Loaded workspace config for agent {}: allowMcps={}, allowSubAgents={}, allowSkills={}, defaultExecutionMode={}, hooksConfigured={}",
                    agentKey, config.getAllowMcps(), config.getAllowSubAgents(), config.getAllowSkills(),
                    config.getDefaultExecutionMode(), config.isHooksConfigured());
            return config;
        } catch (Exception e) {
            log.error("Failed to parse config.yml for agent {}", agentKey, e);
            return new WorkspaceConfig();
        }
    }

    @SuppressWarnings("unchecked")
    private AgentHooksConfig parseHooks(Object rawHooks) {
        if (rawHooks instanceof List<?> list && list.isEmpty()) {
            return AgentHooksConfig.disabled();
        }

        if (!(rawHooks instanceof Map<?, ?> hookMap)) {
            return AgentHooksConfig.empty();
        }

        return AgentHooksConfig.builder()
                .preToolCall(parseHookBindings(hookMap.get("pre-tool-call")))
                .postToolCall(parseHookBindings(hookMap.get("post-tool-call")))
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

    @SuppressWarnings("unchecked")
    private HookBindingConfig parseHookBinding(Map<?, ?> rawBinding) {
        Object enabled = rawBinding.get("enabled");
        Object order = rawBinding.get("order");
        Object tools = rawBinding.get("tools");
        Object options = rawBinding.get("options");

        return HookBindingConfig.builder()
                .bean(rawBinding.get("bean") != null ? String.valueOf(rawBinding.get("bean")) : null)
                .enabled(enabled == null || Boolean.parseBoolean(String.valueOf(enabled)))
                .order(order instanceof Number number ? number.intValue() : parseInt(order))
                .tools(tools instanceof List<?> toolList
                        ? toolList.stream().map(String::valueOf).toList()
                        : List.of("*"))
                .options(options instanceof Map<?, ?> optionMap
                        ? optionMap.entrySet().stream()
                                .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue))
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
                return ModeEnum.valueOf(value.toUpperCase());
            } catch (Exception second) {
                throw new IllegalArgumentException(
                        "Invalid default-execution-mode for agent " + agentKey + ": " + value, second);
            }
        }
    }

    @Data
    public static class WorkspaceConfig {
        private List<String> allowMcps = Collections.emptyList();
        private List<String> allowSubAgents = Collections.emptyList();
        private List<String> allowSkills = Collections.emptyList();
        private ModeEnum defaultExecutionMode;
        private boolean hooksConfigured = false;
        private AgentHooksConfig hooks = AgentHooksConfig.empty();
    }
}
