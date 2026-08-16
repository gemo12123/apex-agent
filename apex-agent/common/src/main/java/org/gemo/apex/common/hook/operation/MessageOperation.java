package org.gemo.apex.common.hook.operation;

public sealed interface MessageOperation permits AppendMessage, ReplaceMessage, RemoveMessage {
    String operationId();
}
