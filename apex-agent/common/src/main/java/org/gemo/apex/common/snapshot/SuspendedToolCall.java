package org.gemo.apex.common.snapshot;

import org.gemo.apex.common.intervention.HumanInterventionRequest;
import org.gemo.apex.common.support.DomainValues;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.*;

public record SuspendedToolCall(String sessionId, long turnNo, int iterationNo, String toolCallId,
                                String invocationId, String toolName, Map<String, Object> resolvedArguments,
                                HumanInterventionRequest intervention, List<String> executedPreToolHookIds,
                                SuspensionPoint suspensionPoint) {
    public SuspendedToolCall {
        sessionId = required(sessionId, "sessionId");
        if (turnNo < 1) throw new IllegalArgumentException("turnNo 必须大于 0");
        if (iterationNo < 1) throw new IllegalArgumentException("iterationNo 必须大于 0");
        toolCallId = required(toolCallId, "toolCallId");
        invocationId = required(invocationId, "invocationId");
        toolName = required(toolName, "toolName");
        resolvedArguments = DomainValues.immutableMap(resolvedArguments, "resolvedArguments");
        intervention = nonNull(intervention, "intervention");
        if (!toolCallId.equals(intervention.toolCallId())) {
            throw new IllegalArgumentException("intervention.toolCallId 与 toolCallId 不一致");
        }
        executedPreToolHookIds = immutableList(executedPreToolHookIds, "executedPreToolHookIds");
        if (new HashSet<>(executedPreToolHookIds).size() != executedPreToolHookIds.size()) {
            throw new IllegalArgumentException("executedPreToolHookIds 不能重复");
        }
        suspensionPoint = nonNull(suspensionPoint, "suspensionPoint");
    }
}
