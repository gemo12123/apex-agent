package org.gemo.apex.component.tool;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.model.McpServerConfig;
import org.gemo.apex.config.provider.SkillConfigProvider;
import org.gemo.apex.service.AgentWorkspaceService;
import org.gemo.apex.tool.skills.CustomSkillsTool;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gemo.apex.constant.CacheKeys;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局统一工具配置与技能注册中心 (Global Tool & Skill Registry)
 * 实现 MCP 工具集以及自定义技能（Skills）的全局统一配置。
 * 每个 Agent 在初始化时可从此服务获取属于该 Agent 的特定工具集和技能列表。
 */
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

    // 工具缓存：key = agentKey + "::" + mcpName，value = 该 MCP 的 ToolCallback 列表
    private final Map<String, List<ToolCallback>> mcpToolCache = new ConcurrentHashMap<>();

    // 技能缓存
    private final Map<String, CustomSkillsTool> skillsCache = new ConcurrentHashMap<>();

    // 已创建的 MCP 客户端（用于关闭时清理进程）
    private final List<McpSyncClient> createdClients = new CopyOnWriteArrayList<>();

    /**
     * 为指定 Agent 获取其配置声明的全部 MCP 工具列表
     */
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
                log.debug("Agent {} 命中 MCP 工具缓存: {}", agentKey, mcpName);
                result.addAll(cached);
                continue;
            }

            McpServerConfig config = agentWorkspaceService.getMcpServerConfig(mcpName);
            if (config == null) {
                log.warn("Agent {} 的全局配置中未找到 MCP Server 定义: {}", agentKey, mcpName);
                continue;
            }

            List<ToolCallback> tools = createMcpTools(agentKey, mcpName, config);
            mcpToolCache.put(cacheKey, tools);
            result.addAll(tools);
        }
        return result;
    }

    /**
     * 为指定 Agent 获取其 CustomSkillsTool 对象
     */
    public CustomSkillsTool getSkillsTool(String agentKey) {
        CustomSkillsTool cached = skillsCache.get(agentKey);
        if (cached != null) {
            return cached;
        }

        List<String> skillNames = agentWorkspaceService.getSkills(agentKey);
        if (skillNames == null || skillNames.isEmpty()) {
            log.debug("Agent {} 没有配置 skills", agentKey);
            return null;
        }

        try {
            CustomSkillsTool.Builder builder = CustomSkillsTool.builder();

            boolean hasValidSkills = false;
            for (String skillName : skillNames) {
                var skillConfig = skillConfigProvider.getSkillConfig(skillName);
                if (skillConfig != null && skillConfig.getDir() != null) {
                    builder.addSkillsDirectory(skillConfig.getDir());
                    hasValidSkills = true;
                } else {
                    log.warn("Agent {} 的技能 {} 未在全局配置中找到或未配置 dir 路径", agentKey, skillName);
                }
            }

            if (!hasValidSkills) {
                log.warn("Agent {} 没有找到任何有效的技能目录配置，跳过技能工具创建", agentKey);
                return null;
            }

            CustomSkillsTool tool = builder.build();
            log.info("Agent {} 加载了 Skills 工具, 技能列表: {}", agentKey, skillNames);

            skillsCache.put(agentKey, tool);
            return tool;
        } catch (Exception e) {
            log.warn("Agent {} 加载 Skills 失败: {}", agentKey, e.getMessage());
            return null;
        }
    }

    /**
     * 获取 SubAgent 工具列表
     * 委托给 SubAgentToolCallbackProvider 构建
     * 通过 agentKey 读取配置以按需拦截或提供特定 SubAgent。
     *
     * @param agentKey 当期会话隶属的 Agent 标识
     * @return SubAgent 工具回调列表
     */
    public List<ToolCallback> getSubAgentToolCallbacks(String agentKey) {
        List<String> allowedSubAgents = agentWorkspaceService.getSubAgents(agentKey);
        return subAgentToolCallbackProvider.buildToolCallbacks(allowedSubAgents);
    }

    /**
     * 清空工具缓存（热更新场景使用）
     */
    public void clearCache() {
        mcpToolCache.clear();
        skillsCache.clear();
        log.info("GlobalToolRegistry 工具缓存已清空");
    }

    @Override
    public void destroy() {
        log.info("正在关闭所有 MCP 客户端，共 {} 个", createdClients.size());
        for (McpSyncClient client : createdClients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端时出错: {}", e.getMessage());
            }
        }
        log.info("所有 MCP 客户端已关闭");
    }

    // ========== 私有方法 ==========

    private List<ToolCallback> createMcpTools(String agentKey, String mcpName,
            McpServerConfig config) {
        try {
            log.info("为 Agent {} 创建 MCP 客户端: {}, command: {} {}",
                    agentKey, mcpName, config.getCommand(), config.getArgs());

            List<String> command = new ArrayList<>();
            command.add(config.getCommand());
            if (config.getArgs() != null) {
                command.addAll(config.getArgs());
            }

            ServerParameters serverParameters = ServerParameters.builder(command.get(0))
                    .args(command.subList(1, command.size()).toArray(new String[0]))
                    .build();

            io.modelcontextprotocol.json.McpJsonMapper jsonMapper = new io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper(
                    new ObjectMapper());

            StdioClientTransport transport = new StdioClientTransport(serverParameters, jsonMapper);

            int timeoutSeconds = config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 60;

            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                    .capabilities(McpSchema.ClientCapabilities.builder().build())
                    .build();

            client.initialize();
            createdClients.add(client);

            List<ToolCallback> tools = Arrays.stream(new SyncMcpToolCallbackProvider(client).getToolCallbacks())
                    .toList();
            log.info("Agent {} 的 MCP 客户端 {} 创建成功，共 {} 个工具",
                    agentKey, mcpName, tools.size());
            return tools;
        } catch (Exception e) {
            log.error("为 Agent {} 创建 MCP 客户端 {} 失败: {}", agentKey, mcpName, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
