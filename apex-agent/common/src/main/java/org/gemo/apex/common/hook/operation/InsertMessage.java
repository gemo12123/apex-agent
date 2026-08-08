package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.*;

import org.gemo.apex.common.message.AgentMessageEntry;

public record InsertMessage(String operationId, int index, AgentMessageEntry message)
        implements MessageOperation {
    public InsertMessage {
        operationId = required(operationId, "operationId");
        nonNegative(index, "index");
        message = nonNull(message, "message");
    }
}
