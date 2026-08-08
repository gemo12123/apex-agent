package org.gemo.apex.runtime.api;

public final class AgentPreparationException extends RuntimeException {
    private final boolean endPublished;

    public AgentPreparationException(Throwable c, boolean e) {
        super("Agent 准备失败", c);
        endPublished = e;
    }

    public boolean endPublished() {
        return endPublished;
    }
}
