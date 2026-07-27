package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Getter;
import org.gemo.apex.hook.tool.ToolConfirmationSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AgentHookResult {
    @Builder.Default
    private final HookFlowAction action = HookFlowAction.CONTINUE;
    @Builder.Default
    private final List<MessageOperation> messageOperations = List.of();
    private final Map<String, Object> updatedToolArguments;
    private final String updatedToolResult;
    private final String blockReason;
    private final ToolConfirmationSpec confirmationSpec;

    public static AgentHookResult continueFlow() {
        return builder().build();
    }

    public static AgentHookResult continueWithMessages(List<MessageOperation> operations) {
        return builder().messageOperations(operations != null ? List.copyOf(operations) : List.of()).build();
    }

    public static AgentHookResult continueWithToolArguments(Map<String, Object> arguments) {
        return builder()
                .updatedToolArguments(arguments != null ? new LinkedHashMap<>(arguments) : new LinkedHashMap<>())
                .build();
    }

    public static AgentHookResult replaceToolResult(String result) {
        return builder().updatedToolResult(result).build();
    }

    public static AgentHookResult skipIteration(List<MessageOperation> operations) {
        return builder().action(HookFlowAction.SKIP_ITERATION)
                .messageOperations(operations != null ? List.copyOf(operations) : List.of())
                .build();
    }

    public static AgentHookResult endTurn(List<MessageOperation> operations) {
        return builder().action(HookFlowAction.END_TURN)
                .messageOperations(operations != null ? List.copyOf(operations) : List.of())
                .build();
    }

    public static AgentHookResult blockTool(String reason) {
        return builder().action(HookFlowAction.BLOCK_TOOL).blockReason(reason).build();
    }

    public static AgentHookResult requestConfirmation(ToolConfirmationSpec spec,
            Map<String, Object> updatedArguments) {
        return builder()
                .action(HookFlowAction.REQUEST_CONFIRMATION)
                .confirmationSpec(spec)
                .updatedToolArguments(updatedArguments)
                .build();
    }
}
