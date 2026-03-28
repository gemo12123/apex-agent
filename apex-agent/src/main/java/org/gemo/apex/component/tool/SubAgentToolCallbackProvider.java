package org.gemo.apex.component.tool;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.provider.AgentConfigProvider;
import org.gemo.apex.tool.SubAgentToolCallback;
import org.gemo.apex.tool.handler.MessageProcessor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SubAgent 工具回调提供者
 *
 * <p>
 * 从 SubAgentProperties 配置中读取各 SubAgent 的服务信息，
 * 为每个配置了 url 的 SubAgent 构建对应的 SubAgentToolCallback。
 *
 * @author gemo
 */
@Slf4j
@Component
public class SubAgentToolCallbackProvider {

    private final AgentConfigProvider agentConfigProvider;
    private final MessageProcessor messageProcessor;

    public SubAgentToolCallbackProvider(
            AgentConfigProvider agentConfigProvider,
            MessageProcessor messageProcessor) {
        this.agentConfigProvider = agentConfigProvider;
        this.messageProcessor = messageProcessor;
    }

    /**
     * 构建 SubAgent 工具列表
     *
     * <p>
     * 仅为配置了 url 的 SubAgent 构建工具回调。
     * 没有配置 url 的 SubAgent 视为本地 Workspace Agent，不参与 HTTP 调用。
     * 同时，如果配置文件（config.yml）中配置了 tool-prefixes，则仅加载那些匹配的子智能体。
     *
     * @param allowedToolPrefixes 配置中允许的工具前缀(可以当做 Agent 名看)
     * @return SubAgent 工具回调列表
     */
    public List<ToolCallback> buildToolCallbacks(List<String> allowedToolPrefixes) {
        if (allowedToolPrefixes == null || allowedToolPrefixes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolCallback> toolCallbacks = new ArrayList<>();

        List<AgentConfig> allAgents = agentConfigProvider.getAllAgents();
        if (allAgents != null) {
            for (AgentConfig config : allAgents) {
                String agentKey = config.getAgentKey();

                // 仅为配置了 url 的 SubAgent 构建 HTTP 调用工具
                if (config.getUrl() == null || config.getUrl().isBlank()) {
                    log.debug("SubAgent {} 未配置 url，跳过工具回调构建", agentKey);
                    continue;
                }

                // 如果存在 allowedToolPrefixes 配置，则判断当前 SubAgent 是否在白名单中
                if (!allowedToolPrefixes.contains(agentKey)) {
                    log.debug("SubAgent {} 不在配置允许的工具前缀列表中，跳过", agentKey);
                    continue;
                }

                String toolName = agentKey;

                ToolCallback subAgentCallback = SubAgentToolCallback.builder()
                        .name(toolName)
                        .displayName(config.getName())
                        .description(config.getDescription())
                        .url(config.getUrl())
                        .timeout(config.getTimeout())
                        .messageProcessor(messageProcessor)
                        .build();

                toolCallbacks.add(subAgentCallback);
                log.debug("添加 SubAgent 工具: {}", toolName);
            }
        }

        log.info("构建 SubAgent 工具列表完成，共 {} 个工具", toolCallbacks.size());
        return toolCallbacks;
    }
}
