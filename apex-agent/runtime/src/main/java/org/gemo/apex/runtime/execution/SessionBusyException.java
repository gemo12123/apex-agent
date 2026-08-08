package org.gemo.apex.runtime.execution;

public final class SessionBusyException extends RuntimeException {
    public SessionBusyException(String id) {
        super("会话正在执行: " + id);
    }
}
