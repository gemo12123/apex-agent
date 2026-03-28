package org.gemo.apex.tool.handler.impl;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.message.ArtifactChangeMessage;
import org.gemo.apex.message.ArtifactDeclaredMessage;
import org.gemo.apex.tool.handler.AbstractMessageHandler;
import org.gemo.apex.tool.handler.MessageHandlerContext;
import org.springframework.stereotype.Component;

/**
 * ARTIFACT 消息处理器
 * 处理 ARTIFACT_DECLARED 和 ARTIFACT_CHANGE 消息
 * 记录日志（后续可扩展为收集产物元数据）
 *
 * @author gemo
 */
@Slf4j
@Component
public class ArtifactMessageHandler extends AbstractMessageHandler {

    @Override
    public boolean canHandle(AgentMessage message) {
        return message instanceof ArtifactDeclaredMessage || message instanceof ArtifactChangeMessage;
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    protected void doHandle(AgentMessage message, MessageHandlerContext context) {
        if (message instanceof ArtifactDeclaredMessage declaredMsg) {
            log.debug("收到 ARTIFACT_DECLARED 消息, artifacts: {}",
                    declaredMsg.getMessages() != null ? declaredMsg.getMessages().size() : 0);
        } else if (message instanceof ArtifactChangeMessage) {
            log.debug("收到 ARTIFACT_CHANGE 消息，忽略");
        }
    }
}
