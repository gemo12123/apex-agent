package org.gemo.apex.common.snapshot;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SuspendedToolBatch(
        String sessionId, long turnNo, int iterationNo, List<PreparedToolCallSnapshot> toolCalls) {
    public SuspendedToolBatch {
        sessionId = required(sessionId, "sessionId");
        if (turnNo < 1) {
            throw new IllegalArgumentException("turnNo 必须大于 0");
        }
        if (iterationNo < 1) {
            throw new IllegalArgumentException("iterationNo 必须大于 0");
        }
        toolCalls = immutableList(toolCalls, "toolCalls");
        if (toolCalls.isEmpty()) {
            throw new IllegalArgumentException("toolCalls 不能为空");
        }
        Set<String> ids = new HashSet<>();
        for (PreparedToolCallSnapshot call : toolCalls) {
            if (!ids.add(call.toolCallId())) {
                throw new IllegalArgumentException("toolCallId 不能重复");
            }
        }
        if (toolCalls.stream()
                .noneMatch(
                        call -> call.disposition() == PreparedToolCallDisposition.INTERVENTION)) {
            throw new IllegalArgumentException("挂起批次必须包含人工介入");
        }
    }
}
