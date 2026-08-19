package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.model.ModelResponse;

public record ModelResponsePatch(ModelResponse replacement) {
    public ModelResponsePatch {
        replacement = nonNull(replacement, "replacement");
    }
}
