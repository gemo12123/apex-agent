package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.shared.SharedDataStore;

public record PreModelCallContext(
        String sessionId, HookBinding binding, ModelRequest request, SharedDataStore sharedData)
        implements HookContextView {
    public PreModelCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        request = nonNull(request, "request");
        sharedData = nonNull(sharedData, "sharedData");
    }
}
