package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ReplacePrompt(PromptDefinition prompt) implements AgentDefinitionOperation {
    public ReplacePrompt {
        prompt = nonNull(prompt, "prompt");
    }
}
