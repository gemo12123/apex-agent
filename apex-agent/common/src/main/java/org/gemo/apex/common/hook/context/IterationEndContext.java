package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.snapshot.IterationSnapshot;

public record IterationEndContext(
        String sessionId,
        HookBinding binding,
        IterationSnapshot iteration,
        SharedDataStore sharedData)
        implements HookContextView {
    public IterationEndContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        iteration = nonNull(iteration, "iteration");
        sharedData = nonNull(sharedData, "sharedData");
    }
}
