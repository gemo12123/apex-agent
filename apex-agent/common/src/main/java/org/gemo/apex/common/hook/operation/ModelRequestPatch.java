package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.model.ModelRequest;

public record ModelRequestPatch(ModelRequest replacement) {
    public ModelRequestPatch {
        replacement = nonNull(replacement, "replacement");
    }
}
