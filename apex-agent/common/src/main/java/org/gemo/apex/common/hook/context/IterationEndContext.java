package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.snapshot.IterationSnapshot;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record IterationEndContext(String sessionId, HookBinding binding,
                                  IterationSnapshot iteration) implements HookContextView {
    public IterationEndContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        iteration = nonNull(iteration, "iteration");
    }
}
