package org.gemo.apex.common.exception;

public class DomainInvariantException extends IllegalArgumentException {
    public DomainInvariantException(String message) {
        super(message);
    }
}
