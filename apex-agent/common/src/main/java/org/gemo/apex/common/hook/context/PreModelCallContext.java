package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.model.ModelRequest;

public record PreModelCallContext(String sessionId, HookBinding binding, ModelRequest request)
        implements HookContextView {
    public PreModelCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        request = nonNull(request, "request");
    }
}
