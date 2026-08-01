package org.gemo.apex.core.exception;

public final class UnavailableToolBindingException extends RuntimeException {
    public UnavailableToolBindingException(String toolName) { super("工具绑定不可用: " + toolName); }
}
