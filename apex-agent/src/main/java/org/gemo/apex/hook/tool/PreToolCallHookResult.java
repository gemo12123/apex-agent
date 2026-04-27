package org.gemo.apex.hook.tool;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class PreToolCallHookResult {

    public enum Outcome {
        PROCEED,
        BLOCK,
        REQUEST_CONFIRMATION
    }

    private final Outcome outcome;
    private final Map<String, Object> updatedArgs;
    private final String blockReason;
    private final ToolConfirmationSpec confirmationSpec;
    @Builder.Default
    private final List<String> executedHookBeans = List.of();

    public static PreToolCallHookResult proceed() {
        return builder().outcome(Outcome.PROCEED).executedHookBeans(List.of()).build();
    }

    public static PreToolCallHookResult proceedWithUpdatedArgs(Map<String, Object> updatedArgs) {
        return builder().outcome(Outcome.PROCEED).updatedArgs(updatedArgs).executedHookBeans(List.of()).build();
    }

    public static PreToolCallHookResult block(String reason) {
        return builder().outcome(Outcome.BLOCK).blockReason(reason).executedHookBeans(List.of()).build();
    }

    public static PreToolCallHookResult requestConfirmation(ToolConfirmationSpec spec) {
        return builder().outcome(Outcome.REQUEST_CONFIRMATION)
                .confirmationSpec(spec)
                .executedHookBeans(List.of())
                .build();
    }
}
