package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.*;

import org.gemo.apex.common.message.AgentMessageEntry;

public record ReplaceMessage(String operationId, int index, AgentMessageEntry message)
        implements MessageOperation {
    public ReplaceMessage {
        operationId = required(operationId, "operationId");
        nonNegative(index, "index");
        message = nonNull(message, "message");
    }
}
