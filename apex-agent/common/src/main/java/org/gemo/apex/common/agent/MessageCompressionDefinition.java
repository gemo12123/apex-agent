package org.gemo.apex.common.agent;

public record MessageCompressionDefinition(
        boolean enabled, int maxMessages, Long tokenThreshold, Long characterHardLimit) {
    public MessageCompressionDefinition {
        if (maxMessages < 1) {
            throw new IllegalArgumentException("maxMessages 必须大于 0");
        }
        if (tokenThreshold != null && tokenThreshold < 1) {
            throw new IllegalArgumentException("tokenThreshold 必须大于 0");
        }
        if (characterHardLimit != null && characterHardLimit < 1) {
            throw new IllegalArgumentException("characterHardLimit 必须大于 0");
        }
    }

    public MessageCompressionDefinition(boolean enabled, int maxMessages) {
        this(enabled, maxMessages, null, null);
    }
}
