package org.gemo.apex.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.McpServerConfig;
import org.gemo.apex.config.provider.AgentConfigProvider;
import org.gemo.apex.config.provider.McpConfigProvider;
import org.gemo.apex.config.provider.SkillConfigProvider;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.prompt.StageSystemPrompt;
import org.gemo.apex.context.SuperAgentContext;
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

/**
 * Agent Workspace 服务
 * 负责读取 Agent 独立工作空间目录下的配置和资源文件
 */
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

    public String getThinkingPrompt(String agentKey) {
        String content = readWorkspaceFile(agentKey, "THINKING_PROMPT.md");
        if (content == null) {
            content = readDefaultFile("THINKING_PROMPT.md");
        }
        return content != null ? content : StageSystemPrompt.getThinkingPrompt();
    }

    public String getModeConfirmationPrompt(String agentKey) {
        String content = readWorkspaceFile(agentKey, "MODE_CONFIRMATION_PROMPT.md");
        if (content == null) {
            content = readDefaultFile("MODE_CONFIRMATION_PROMPT.md");
        }
        return content != null ? content : StageSystemPrompt.getModeConfirmationPrompt();
    }

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
            log.debug("Skills 目录不存在: {}/skills/", workspacePath);
        }
        return null;
    }

    public WorkspaceConfig getWorkspaceConfig(String agentKey) {
        return configCache.computeIfAbsent(agentKey, this::loadWorkspaceConfig);
    }

    public SuperAgentContext.Stage getDefaultStartStage(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getDefaultStartStage() != null) {
            return config.getDefaultStartStage();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        if (agentConfig != null && agentConfig.getDefaultStartStage() != null) {
            return agentConfig.getDefaultStartStage();
        }
        return SuperAgentContext.Stage.THINKING;
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

    /**
     * 优先级：config.yml (allow-mcps) > application.yml (mcps)
     */
    public List<String> getMcpNames(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getAllowMcps() != null && !config.getAllowMcps().isEmpty()) {
            return config.getAllowMcps();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        return agentConfig != null && agentConfig.getMcps() != null ? agentConfig.getMcps() : Collections.emptyList();
    }

    /**
     * 优先级：config.yml (allow-sub-agents) > application.yml (subAgents)
     */
    public List<String> getSubAgents(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getAllowSubAgents() != null && !config.getAllowSubAgents().isEmpty()) {
            return config.getAllowSubAgents();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        return agentConfig != null && agentConfig.getSubAgents() != null ? agentConfig.getSubAgents()
                : Collections.emptyList();
    }

    /**
     * 优先级：config.yml (allow-skills) > application.yml (skills)
     */
    public List<String> getSkills(String agentKey) {
        WorkspaceConfig config = getWorkspaceConfig(agentKey);
        if (config.getAllowSkills() != null && !config.getAllowSkills().isEmpty()) {
            return config.getAllowSkills();
        }
        AgentConfig agentConfig = agentConfigProvider.getAgentConfig(agentKey);
        return agentConfig != null && agentConfig.getSkills() != null ? agentConfig.getSkills()
                : Collections.emptyList();
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
                    log.debug("读取文件成功: {}", resourcePath);
                    return content;
                }
            }
        } catch (IOException e) {
            log.warn("读取文件失败: {}, 错误: {}", resourcePath, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private WorkspaceConfig loadWorkspaceConfig(String agentKey) {
        String configContent = readWorkspaceFile(agentKey, "config.yml");
        if (configContent == null) {
            log.info("Agent {} 没有 config.yml，使用空配置", agentKey);
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
            if (map.containsKey("default-start-stage")) {
                config.setDefaultStartStage(parseStage(map.get("default-start-stage"), agentKey));
            }
            if (map.containsKey("default-execution-mode")) {
                config.setDefaultExecutionMode(parseMode(map.get("default-execution-mode"), agentKey));
            }

            log.info(
                    "加载 Agent {} 的 Workspace 配置: allowMcps={}, allowSubAgents={}, allowSkills={}, defaultStartStage={}, defaultExecutionMode={}",
                    agentKey, config.getAllowMcps(), config.getAllowSubAgents(), config.getAllowSkills(),
                    config.getDefaultStartStage(), config.getDefaultExecutionMode());
            return config;
        } catch (Exception e) {
            log.error("解析 Agent {} 的 config.yml 失败", agentKey, e);
            return new WorkspaceConfig();
        }
    }

    private SuperAgentContext.Stage parseStage(Object rawValue, String agentKey) {
        String value = String.valueOf(rawValue).trim();
        try {
            return SuperAgentContext.Stage.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid default-start-stage for agent " + agentKey + ": " + value, e);
        }
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
        private SuperAgentContext.Stage defaultStartStage;
        private ModeEnum defaultExecutionMode;
    }
}
