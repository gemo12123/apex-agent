package org.gemo.apex.platform.web;

import java.util.List;
import java.util.Map;

public record ConversationHistoryView(
        String sessionId,
        String agentKey,
        String executionStatus,
        List<Turn> turns) {
    public record Turn(long no, String question, List<Iteration> iterations) {}

    public record Iteration(int no, List<Block> blocks) {}

    public record Block(
            String type,
            String id,
            String content,
            String toolName,
            Map<String, Object> arguments,
            Map<String, Object> resolvedArguments,
            String result) {}
}
