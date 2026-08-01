package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.message.AgentMessageEntry;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record AppendMessage(String operationId, AgentMessageEntry message) implements MessageOperation {
    public AppendMessage {
        operationId = required(operationId, "operationId");
        message = nonNull(message, "message");
    }
}
