package org.gemo.apex.tool.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.message.InvocationChangeMessage;
import org.gemo.apex.message.InvocationDeclaredMessage;
import org.gemo.apex.tool.handler.AbstractMessageHandler;
import org.gemo.apex.tool.handler.MessageHandlerContext;
import org.gemo.apex.tool.metadata.CustomToolMetadata;
import org.gemo.apex.util.MessageUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * INVOCATION 消息处理器
 * 处理 INVOCATION_DECLARED 和 INVOCATION_CHANGE 消息
 * 记录日志并向前端推送进度
 *
 * @author gemo
 */
@Slf4j
@Component
public class InvocationMessageHandler extends AbstractMessageHandler {

    @Override
    public boolean canHandle(AgentMessage message) {
        return message instanceof InvocationDeclaredMessage || message instanceof InvocationChangeMessage;
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    protected void doHandle(AgentMessage message, MessageHandlerContext context) {
        if (message instanceof InvocationDeclaredMessage declaredMsg) {
            log.debug("收到 INVOCATION_DECLARED 消息, invocations: {}",
                    declaredMsg.getMessages() != null ? declaredMsg.getMessages().size() : 0);
        } else if (message instanceof InvocationChangeMessage changeMsg) {
            log.debug("收到 INVOCATION_CHANGE 消息, changes: {}",
                    changeMsg.getMessages() != null ? changeMsg.getMessages().size() : 0);
        }

        // 1. 从 context 获取 mode 和 stageId（如果处于 PlanExecutor 模式）及 executor
        Map<String, Object> enrichedContext = enrichWithContext(
                message.getContext(),
                context.getSuperAgentContext(),
                context.getToolContext());
        message.setContext(enrichedContext);

        // 2. 通过 SSE 发送
        context.getSuperAgentContext().ifPresent(superAgentContext -> {
            MessageUtils.sendMessage(superAgentContext, message);
        });
    }

    private Map<String, Object> enrichWithContext(
            Map<String, Object> originalContext,
            Optional<SuperAgentContext> superAgentContextOpt,
            Optional<Map<String, Object>> toolContextOpt) {
        Map<String, Object> enriched = originalContext != null ? new HashMap<>(originalContext) : new HashMap<>();

        superAgentContextOpt.ifPresent(superAgentContext -> {
            ModeEnum mode = superAgentContext.getExecutionMode();
            if (mode != null) {
                enriched.put(ContextKeyEnum.MODE.getKey(), mode.getMode());
                if (ModeEnum.PLAN_EXECUTOR.equals(mode)) {
                    enriched.put(ContextKeyEnum.STAGE_ID.getKey(), superAgentContext.getCurrentStageId());
                }
            }
        });

        if (toolContextOpt.isPresent()) {
            Map<String, Object> toolCtx = toolContextOpt.get();
            Object customToolMetadata = toolCtx.get(ToolContextKeys.CUSTOM_TOOL_METADATA);
            if (customToolMetadata instanceof CustomToolMetadata ctm) {
                enriched.put(ContextKeyEnum.EXECUTOR.getKey(), ctm.getDisplayName());
            }
        }

        return enriched;
    }
}
