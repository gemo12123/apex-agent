package org.gemo.apex.component;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.util.ToolExecutionMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;
import org.gemo.apex.tool.metadata.CustomToolMetadata;

@Component
public class DefaultToolInvocationNotifier implements ToolInvocationNotifier {

    private static final Logger logger = LoggerFactory.getLogger(DefaultToolInvocationNotifier.class);

    @Override
    public void beforeExecution(ToolCallback toolCallback, SuperAgentContext context, String invocationId) {
        ToolDefinition toolDefinition = toolCallback.getToolDefinition();
        String toolName = toolDefinition.name();
        String toolDescription = toolDefinition.description();

        ToolMetadata toolMetadata = toolCallback.getToolMetadata();
        if (toolMetadata instanceof CustomToolMetadata customMetadata) {
            String type = customMetadata.getType();
            String name = customMetadata.getName();
            String description = customMetadata.getDescription();

            if (CustomToolMetadata.ToolMetadataAttribute.TYPE_MCP.equals(type)) {
                logger.info("正在执行 MCP - {} - {}", toolName, toolDescription);
                ToolExecutionMessageHelper.sendMcpExecutionStart(toolCallback, context, invocationId);
            } else if (CustomToolMetadata.ToolMetadataAttribute.TYPE_SUB_AGENT.equals(type)) {
                logger.info("正在执行 SubAgent - {} - {}", name, description);
            } else {
                logger.info("正在执行工具 - {} - {}", name, description);
            }
        } else {
            logger.info("正在执行工具 - {} - {}", toolName, toolDescription);
        }
    }

    @Override
    public void afterExecution(ToolCallback toolCallback, SuperAgentContext context, String invocationId) {
        ToolDefinition toolDefinition = toolCallback.getToolDefinition();
        ToolMetadata toolMetadata = toolCallback.getToolMetadata();

        if (toolMetadata instanceof CustomToolMetadata customMetadata) {
            String type = customMetadata.getType();
            String name = customMetadata.getName();
            String description = customMetadata.getDescription();

            if (CustomToolMetadata.ToolMetadataAttribute.TYPE_MCP.equals(type)) {
                logger.info("MCP执行完成 - {} - {}", name, description);
                ToolExecutionMessageHelper.sendMcpExecutionComplete(toolCallback, context, invocationId);
            } else if (CustomToolMetadata.ToolMetadataAttribute.TYPE_SUB_AGENT.equals(type)) {
                logger.info("SubAgent执行完成 - {} - {}", name, description);
            } else {
                logger.info("工具执行完成 - {} - {}", name, description);
            }
        } else {
            logger.info("工具执行完成 - {} - {}", toolDefinition.name(), toolDefinition.description());
        }
    }
}
