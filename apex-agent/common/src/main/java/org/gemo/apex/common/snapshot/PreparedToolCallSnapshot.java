package org.gemo.apex.common.snapshot;

import org.gemo.apex.common.intervention.HumanInterventionRequest;
import org.gemo.apex.common.intervention.HumanSubmission;
import org.gemo.apex.common.support.DomainValues;
import org.gemo.apex.common.tool.ToolResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.*;

public record PreparedToolCallSnapshot(String toolCallId, String invocationId, String toolName, int ordinal,
                                       Map<String, Object> resolvedArguments,
                                       List<String> executedPreToolHookIds,
                                       PreparedToolCallDisposition disposition,
                                       ToolResult result,
                                       HumanInterventionRequest intervention,
                                       HumanSubmission submission) {
    public PreparedToolCallSnapshot {
        toolCallId = required(toolCallId, "toolCallId");
        invocationId = required(invocationId, "invocationId");
        toolName = required(toolName, "toolName");
        if (ordinal < 0) throw new IllegalArgumentException("ordinal 不能小于 0");
        resolvedArguments = DomainValues.immutableMap(resolvedArguments, "resolvedArguments");
        executedPreToolHookIds = immutableList(executedPreToolHookIds, "executedPreToolHookIds");
        if (new HashSet<>(executedPreToolHookIds).size() != executedPreToolHookIds.size()) {
            throw new IllegalArgumentException("executedPreToolHookIds 不能重复");
        }
        disposition = nonNull(disposition, "disposition");
        switch (disposition) {
            case EXECUTE -> {
                if (result != null || intervention != null) {
                    throw new IllegalArgumentException("EXECUTE 不能携带 result/intervention");
                }
            }
            case RETURN_RESULT -> {
                if (result == null || intervention != null) {
                    throw new IllegalArgumentException("RETURN_RESULT 必须且只能携带 result");
                }
                validateResult(toolCallId, toolName, result);
            }
            case INTERVENTION -> {
                if (intervention == null || result != null) {
                    throw new IllegalArgumentException("INTERVENTION 必须且只能携带 intervention");
                }
                if (!toolCallId.equals(intervention.toolCallId())) {
                    throw new IllegalArgumentException("intervention.toolCallId 与 toolCallId 不一致");
                }
            }
        }
        if (submission != null && !toolCallId.equals(submission.toolCallId())) {
            throw new IllegalArgumentException("submission.toolCallId 与 toolCallId 不一致");
        }
    }

    private static void validateResult(String toolCallId, String toolName, ToolResult result) {
        if (!toolCallId.equals(result.toolCallId()) || !toolName.equals(result.toolName())) {
            throw new IllegalArgumentException("result 与 ToolCall ID/name 不一致");
        }
    }
}
