package org.gemo.apex.tool.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.message.PlanChangeMessage;
import org.gemo.apex.message.PlanDeclaredMessage;
import org.gemo.apex.message.TaskThinkChangeMessage;
import org.gemo.apex.message.TaskThinkDeclaredMessage;
import org.gemo.apex.tool.handler.AbstractMessageHandler;
import org.gemo.apex.tool.handler.MessageHandlerContext;
import org.springframework.stereotype.Component;

/**
 * 空操作消息处理器
 * 用于忽略 PLAN_DECLARED、PLAN_CHANGE、TASK_THINK_DECLARED、TASK_THINK_CHANGE 消息
 *
 * @author gemo
 */
@Slf4j
@Component
public class NoOpMessageHandler extends AbstractMessageHandler {

    @Override
    public boolean canHandle(AgentMessage message) {
        return message instanceof PlanDeclaredMessage || message instanceof PlanChangeMessage ||
                message instanceof TaskThinkDeclaredMessage || message instanceof TaskThinkChangeMessage;
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    protected void doHandle(AgentMessage message, MessageHandlerContext context) {
        // 直接忽略，不做任何处理
    }
}
