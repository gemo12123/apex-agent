package org.gemo.apex.common.agent;

public record MessageCompressionDefinition(boolean enabled, int maxMessages) {
    public MessageCompressionDefinition {
        if (maxMessages < 1) {
            throw new IllegalArgumentException("maxMessages 必须大于 0");
        }
    }
}
