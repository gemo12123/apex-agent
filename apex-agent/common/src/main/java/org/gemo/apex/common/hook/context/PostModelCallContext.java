package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.model.ModelResponse;

public record PostModelCallContext(String sessionId, HookBinding binding, ModelResponse response)
        implements HookContextView {
    public PostModelCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        response = nonNull(response, "response");
    }
}
