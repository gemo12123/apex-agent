package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.nonNegative;
import static org.gemo.apex.common.support.DomainValues.required;

public record RemoveMessage(String operationId, int index) implements MessageOperation {
    public RemoveMessage {
        operationId = required(operationId, "operationId");
        nonNegative(index, "index");
    }
}
