package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.required;

public record RemoveMessage(String operationId, String targetEntryId) implements MessageOperation {
    public RemoveMessage {
        operationId = required(operationId, "operationId");
        targetEntryId = required(targetEntryId, "targetEntryId");
    }
}
