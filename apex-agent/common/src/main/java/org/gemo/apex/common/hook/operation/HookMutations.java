package org.gemo.apex.common.hook.operation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.nonNull;

public record HookMutations(List<MessageOperation> messageOperations,
                            ToolActivationDelta toolActivationDelta) {
    public HookMutations {
        messageOperations = immutableList(messageOperations, "messageOperations");
        toolActivationDelta = nonNull(toolActivationDelta, "toolActivationDelta");
        Set<String> ids = new HashSet<>();
        for (MessageOperation operation : messageOperations) {
            if (!ids.add(operation.operationId())) {
                throw new IllegalArgumentException("messageOperations.operationId 重复: " + operation.operationId());
            }
        }
    }

    public static HookMutations none() {
        return new HookMutations(List.of(), ToolActivationDelta.none());
    }
}
