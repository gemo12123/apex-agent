package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.model.ModelResponse;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ModelResponsePatch(ModelResponse replacement) {
    public ModelResponsePatch { replacement = nonNull(replacement, "replacement"); }
}
