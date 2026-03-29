package org.gemo.apex.core.engine;

public record ToolCallProcessingResult(boolean directAnswerTriggered) {

    public static ToolCallProcessingResult continueLoop() {
        return new ToolCallProcessingResult(false);
    }

    public static ToolCallProcessingResult terminateLoop() {
        return new ToolCallProcessingResult(true);
    }
}
