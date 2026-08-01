package org.gemo.apex.core.exception;

public final class HookContractException extends RuntimeException {
    public HookContractException(String message) { super(message); }
    public HookContractException(String message, Throwable cause) { super(message, cause); }
}
