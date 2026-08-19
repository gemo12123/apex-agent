package org.gemo.apex.extension.model;

import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;

public interface ModelGateway {
    ModelResponse stream(ModelRequest request, ModelStreamObserver observer);
}
