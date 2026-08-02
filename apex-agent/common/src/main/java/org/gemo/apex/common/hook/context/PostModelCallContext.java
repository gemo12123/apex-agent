package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.model.ModelResponse;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record PostModelCallContext(String sessionId, HookBinding binding,
                                   ModelResponse response) implements HookContextView {
    public PostModelCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        response = nonNull(response, "response");
    }
}
