package org.gemo.apex.util;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.context.SuperAgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 工具执行消息辅助类
 * 用于在工具执行前后记录日志（后续可扩展为 SSE 推送）
 *
 * @author Super-Zhang
 * @date 2026/1/15
 */
@Slf4j
public class ToolExecutionMessageHelper {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionMessageHelper.class);

    /**
     * 私有构造函数，防止实例化
     */
    private ToolExecutionMessageHelper() {
    }

    /**
     * 发送工具执行开始消息
     *
     * <p>
     * 当前实现为日志输出，后续可扩展为通过 SSE 或事件总线推送给前端。
     *
     * @param toolCallback 工具回调对象
     * @param context      SuperAgent 会话上下文
     * @param invocationId 调用唯一标识
     */
    public static void sendMcpExecutionStart(ToolCallback toolCallback, SuperAgentContext context,
            String invocationId) {
        if (context == null) {
            logger.debug("SuperAgentContext 为空，跳过发送工具执行开始消息");
            return;
        }

        ToolDefinition toolDefinition = toolCallback.getToolDefinition();
        String toolName = toolDefinition.name();
        String toolDescription = toolDefinition.description();

        // TODO: 后续可通过事件总线或 SSE 推送给前端
        logger.info("[{}] 工具执行开始 - {} ({}), sessionId: {}",
                invocationId, toolName, toolDescription, context.getSessionId());
    }

    /**
     * 发送工具执行完成消息
     *
     * <p>
     * 当前实现为日志输出，后续可扩展为通过 SSE 或事件总线推送给前端。
     *
     * @param toolCallback 工具回调对象
     * @param context      SuperAgent 会话上下文
     * @param invocationId 调用唯一标识
     */
    public static void sendMcpExecutionComplete(ToolCallback toolCallback, SuperAgentContext context,
            String invocationId) {
        if (context == null) {
            logger.debug("SuperAgentContext 为空，跳过发送工具执行完成消息");
            return;
        }

        ToolDefinition toolDefinition = toolCallback.getToolDefinition();
        String toolName = toolDefinition.name();

        // TODO: 后续可通过事件总线或 SSE 推送给前端
        logger.info("[{}] 工具执行完成 - {}, sessionId: {}",
                invocationId, toolName, context.getSessionId());
    }
}
