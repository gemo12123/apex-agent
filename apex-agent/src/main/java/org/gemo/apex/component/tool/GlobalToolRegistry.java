package org.gemo.apex.component.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.model.McpServerConfig;
import org.gemo.apex.config.provider.SkillConfigProvider;
import org.gemo.apex.constant.CacheKeys;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.service.AgentWorkspaceService;
import org.gemo.apex.tool.skills.FileSystemSkill;
import org.gemo.apex.tool.skills.FileSystemSkillLoader;
import org.gemo.apex.tool.skills.Skills;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.nio.file.Path;

@Slf4j
@Component
public class GlobalToolRegistry implements DisposableBean {

    @Autowired
    private AgentWorkspaceService agentWorkspaceService;

    @Autowired
    private SkillConfigProvider skillConfigProvider;

    @Lazy
    @Autowired
    private SubAgentToolCallbackProvider subAgentToolCallbackProvider;

    private final Map<String, List<ToolCallback>> mcpToolCache = new ConcurrentHashMap<>();
    private final Map<String, Skills> skillsCache = new ConcurrentHashMap<>();
    private final List<McpSyncClient> createdClients = new CopyOnWriteArrayList<>();

    public List<ToolCallback> getMcpToolCallbacks(String agentKey) {
        List<String> mcpNames = agentWorkspaceService.getMcpNames(agentKey);
        if (mcpNames == null || mcpNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolCallback> result = new ArrayList<>();
        for (String mcpName : mcpNames) {
            String cacheKey = agentKey + CacheKeys.MCP_SEPARATOR + mcpName;
            List<ToolCallback> cached = mcpToolCache.get(cacheKey);
            if (cached != null) {
                log.debug("Agent {} hit MCP tool cache: {}", agentKey, mcpName);
                result.addAll(cached);
                continue;
            }

            McpServerConfig config = agentWorkspaceService.getMcpServerConfig(mcpName);
            if (config == null) {
                log.warn("Agent {} missing MCP server config: {}", agentKey, mcpName);
                continue;
            }

            List<ToolCallback> tools = createMcpTools(agentKey, mcpName, config);
            mcpToolCache.put(cacheKey, tools);
            result.addAll(tools);
        }
        return result;
    }

    public Skills getSkillsTool(String agentKey) {
        Skills cached = skillsCache.get(agentKey);
        if (cached != null) {
            return cached;
        }

        List<String> skillNames = agentWorkspaceService.getSkills(agentKey);
        if (skillNames == null || skillNames.isEmpty()) {
            log.debug("Agent {} has no configured skills", agentKey);
            return null;
        }

        try {
            List<FileSystemSkill> loadedSkills = new ArrayList<>();

            for (String skillName : skillNames) {
                var skillConfig = skillConfigProvider.getSkillConfig(skillName);
                if (skillConfig != null && skillConfig.getDir() != null) {
                    loadedSkills.add(FileSystemSkillLoader.loadSkill(Path.of(skillConfig.getDir())));
                } else {
                    log.warn("Agent {} missing skill directory config for {}", agentKey, skillName);
                }
            }

            if (loadedSkills.isEmpty()) {
                log.warn("Agent {} has no valid skill directories", agentKey);
                return null;
            }

            Skills tool = Skills.from(loadedSkills);
            log.info("Agent {} loaded skills {}", agentKey, skillNames);
            skillsCache.put(agentKey, tool);
            return tool;
        } catch (Exception e) {
            log.warn("Agent {} failed to load skills: {}", agentKey, e.getMessage());
            return null;
        }
    }

    public List<ToolCallback> getSubAgentToolCallbacks(String agentKey) {
        List<String> allowedSubAgents = agentWorkspaceService.getSubAgents(agentKey);
        return subAgentToolCallbackProvider.buildToolCallbacks(allowedSubAgents);
    }

    public void clearCache() {
        mcpToolCache.clear();
        skillsCache.clear();
        log.info("GlobalToolRegistry cache cleared");
    }

    @Override
    public void destroy() {
        log.info("Closing {} MCP clients", createdClients.size());
        for (McpSyncClient client : createdClients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Failed to close MCP client: {}", e.getMessage());
            }
        }
        log.info("All MCP clients closed");
    }

    private List<ToolCallback> createMcpTools(String agentKey, String mcpName, McpServerConfig config) {
        try {
            log.info("Creating MCP client for agent {} and server {}: {} {}", agentKey, mcpName,
                    config.getCommand(), config.getArgs());

            List<String> command = new ArrayList<>();
            command.add(config.getCommand());
            if (config.getArgs() != null) {
                command.addAll(config.getArgs());
            }

            ServerParameters serverParameters = ServerParameters.builder(command.get(0))
                    .args(command.subList(1, command.size()).toArray(new String[0]))
                    .build();

            io.modelcontextprotocol.json.McpJsonMapper jsonMapper =
                    new io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper(new ObjectMapper());
            StdioClientTransport transport = new StdioClientTransport(serverParameters, jsonMapper);

            int timeoutSeconds = config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 60;
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                    .capabilities(McpSchema.ClientCapabilities.builder().build())
                    .build();

            client.initialize();
            createdClients.add(client);

            List<ToolCallback> tools = Arrays.stream(SyncMcpToolCallbackProvider.builder()
                    .mcpClients(client)
                    .toolContextToMcpMetaConverter(buildToolContextToMcpMetaConverter())
                    .build()
                    .getToolCallbacks()).toList();
            log.info("Created {} MCP tools for agent {} and server {}", tools.size(), agentKey, mcpName);
            return tools;
        } catch (Exception e) {
            log.error("Failed to create MCP client for agent {} and server {}", agentKey, mcpName, e);
            return Collections.emptyList();
        }
    }

    private ToolContextToMcpMetaConverter buildToolContextToMcpMetaConverter() {
        return toolContext -> {
            if (toolContext == null || toolContext.getContext() == null) {
                return Map.of();
            }

            Object snapshot = toolContext.getContext().get(ToolContextKeys.MCP_SESSION_CONTEXT);
            if (!(snapshot instanceof Map<?, ?> snapshotMap) || snapshotMap.isEmpty()) {
                return Map.of();
            }

            Map<String, Object> sessionMeta = new LinkedHashMap<>();
            snapshotMap.forEach((key, value) -> {
                if (key instanceof String stringKey && value != null) {
                    sessionMeta.put(stringKey, value);
                }
            });
            if (sessionMeta.isEmpty()) {
                return Map.of();
            }

            return Map.of(ToolContextKeys.SESSION_CONTEXT, Map.copyOf(sessionMeta));
        };
    }
}
