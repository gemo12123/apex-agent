package org.gemo.apex.core.lifecycle;

import java.util.List;

public record PreToolDispatchOutcome(LifecycleDispatchOutcome outcome,
                                     List<String> executedBindingIds) {
    public PreToolDispatchOutcome {
        executedBindingIds = List.copyOf(executedBindingIds);
    }
}
