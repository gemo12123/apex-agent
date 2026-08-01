package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.message.AgentMessageEntry;

import static org.gemo.apex.common.support.DomainValues.*;

public record ReplaceMessage(String operationId, int index, AgentMessageEntry message) implements MessageOperation {
    public ReplaceMessage {
        operationId = required(operationId, "operationId");
        nonNegative(index, "index");
        message = nonNull(message, "message");
    }
}
