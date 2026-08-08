package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.immutableNames;

import java.util.HashSet;
import java.util.Set;

public record ToolActivationDelta(Set<String> enable, Set<String> disable) {
    public ToolActivationDelta {
        enable = immutableNames(enable, "enable");
        disable = immutableNames(disable, "disable");
        Set<String> overlap = new HashSet<>(enable);
        overlap.retainAll(disable);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("工具不能同时启用和禁用: " + overlap);
        }
    }

    public static ToolActivationDelta none() {
        return new ToolActivationDelta(Set.of(), Set.of());
    }
}
