package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.model.ModelRequest;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record PreModelCallContext(String sessionId, HookBinding binding,
                                  ModelRequest request) implements HookContextView {
    public PreModelCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        request = nonNull(request, "request");
    }
}
