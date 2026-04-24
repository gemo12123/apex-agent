package org.gemo.apex.hook.tool;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostToolCallHookResult {

    public enum Outcome {
        KEEP,
        REPLACE_RESULT
    }

    private final Outcome outcome;
    private final String nextResult;

    public static PostToolCallHookResult keep() {
        return builder().outcome(Outcome.KEEP).build();
    }

    public static PostToolCallHookResult replaceResult(String nextResult) {
        return builder().outcome(Outcome.REPLACE_RESULT).nextResult(nextResult).build();
    }
}
