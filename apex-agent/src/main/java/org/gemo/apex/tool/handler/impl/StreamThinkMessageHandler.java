package org.gemo.apex.tool.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.message.StreamThinkMessage;
import org.gemo.apex.tool.handler.AbstractMessageHandler;
import org.gemo.apex.tool.handler.MessageHandlerContext;
import org.springframework.stereotype.Component;

/**
 * STREAM_THINK 消息处理器
 * 处理思考流消息，仅记录日志
 *
 * @author gemo
 */
@Slf4j
@Component
public class StreamThinkMessageHandler extends AbstractMessageHandler {

    @Override
    public boolean canHandle(AgentMessage message) {
        return message instanceof StreamThinkMessage;
    }

    @Override
    public int getOrder() {
        return 10; // 高优先级
    }

    @Override
    protected void doHandle(AgentMessage message, MessageHandlerContext context) {
        // 思考内容仅记录日志，不拼接到结果中
        log.debug("收到 STREAM_THINK 消息");
    }
}
