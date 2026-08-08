package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.message.AgentMessageEntry;

public record AppendMessage(String operationId, AgentMessageEntry message)
        implements MessageOperation {
    public AppendMessage {
        operationId = required(operationId, "operationId");
        message = nonNull(message, "message");
    }
}
