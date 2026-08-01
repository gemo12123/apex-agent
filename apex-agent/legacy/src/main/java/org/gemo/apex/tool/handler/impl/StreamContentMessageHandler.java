package org.gemo.apex.tool.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.message.StreamContentMessage;
import org.gemo.apex.tool.handler.AbstractMessageHandler;
import org.gemo.apex.tool.handler.MessageHandlerContext;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * STREAM_CONTENT 消息处理器
 * 处理正文流消息，累积内容到 resultBuilder
 *
 * @author gemo
 */
@Slf4j
@Component
public class StreamContentMessageHandler extends AbstractMessageHandler {

    @Override
    public boolean canHandle(AgentMessage message) {
        return message instanceof StreamContentMessage;
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    protected void doHandle(AgentMessage message, MessageHandlerContext context) {
        StreamContentMessage streamContent = (StreamContentMessage) message;

        // 提取内容并拼接到 resultBuilder
        String content = extractContent(streamContent);
        context.getResultBuilder().append(content);
    }

    /**
     * 提取正文内容
     *
     * @param message STREAM_CONTENT 消息
     * @return 正文内容
     */
    private String extractContent(StreamContentMessage message) {
        if (message.getMessages() == null) {
            return "";
        }
        return message.getMessages().stream()
                .map(StreamContentMessage.ContentMessage::getContent)
                .collect(Collectors.joining(""));
    }
}
