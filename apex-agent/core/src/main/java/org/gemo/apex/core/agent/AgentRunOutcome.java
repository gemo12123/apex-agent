package org.gemo.apex.core.agent;

public sealed interface AgentRunOutcome {
    record Completed() implements AgentRunOutcome {}

    record EndedByHook(String reason) implements AgentRunOutcome {}

    record Suspended() implements AgentRunOutcome {}

    record Cancelled() implements AgentRunOutcome {}

    record Failed(Throwable cause) implements AgentRunOutcome {}
}
