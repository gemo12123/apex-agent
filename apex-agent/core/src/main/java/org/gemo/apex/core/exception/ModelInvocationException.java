package org.gemo.apex.core.exception;

public final class ModelInvocationException extends RuntimeException {
    public ModelInvocationException(RuntimeException cause) {
        super(cause.getMessage(), cause);
    }
}
