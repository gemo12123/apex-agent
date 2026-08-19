package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.required;

public record PromptDefinition(String systemPrompt, int maxIterations) {
    public PromptDefinition {
        systemPrompt = required(systemPrompt, "systemPrompt");
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations 必须大于 0");
        }
    }
}
