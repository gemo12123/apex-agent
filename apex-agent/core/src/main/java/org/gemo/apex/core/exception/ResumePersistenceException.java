package org.gemo.apex.core.exception;

public final class ResumePersistenceException extends RuntimeException {
    public ResumePersistenceException(RuntimeException cause) {
        super("恢复结果持久化失败，保留原挂起快照以允许重试", cause);
    }
}
