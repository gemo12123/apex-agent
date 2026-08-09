package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.immutableNames;

import java.util.HashSet;
import java.util.Set;

public record SkillActivationDelta(Set<String> activate, Set<String> deactivate) {
    public SkillActivationDelta {
        activate = immutableNames(activate, "activate");
        deactivate = immutableNames(deactivate, "deactivate");
        Set<String> overlap = new HashSet<>(activate);
        overlap.retainAll(deactivate);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Skill 不能同时激活和停用: " + overlap);
        }
    }

    public static SkillActivationDelta none() {
        return new SkillActivationDelta(Set.of(), Set.of());
    }
}
