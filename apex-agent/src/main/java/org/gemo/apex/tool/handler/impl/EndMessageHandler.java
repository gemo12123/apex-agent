package org.gemo.apex.tool.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.tool.handler.AbstractMessageHandler;
import org.gemo.apex.tool.handler.MessageHandlerContext;
import org.springframework.stereotype.Component;

/**
 * END 消息处理器
 * 处理会话结束消息
 *
 * @author gemo
 */
@Slf4j
@Component
public class EndMessageHandler extends AbstractMessageHandler {

    @Override
    public boolean canHandle(AgentMessage message) {
        return message instanceof EndMessage;
    }

    @Override
    public int getOrder() {
        return 100; // 最低优先级，最后处理
    }

    @Override
    protected void doHandle(AgentMessage message, MessageHandlerContext context) {
        log.info("收到 END 消息，标记处理完成");
        context.markComplete();
    }
}
