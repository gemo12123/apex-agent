package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.model.ModelRequest;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ModelRequestPatch(ModelRequest replacement) {
    public ModelRequestPatch { replacement = nonNull(replacement, "replacement"); }
}
