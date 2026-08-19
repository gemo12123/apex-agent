package org.gemo.apex.core.exception;

public final class SuspensionEventPublishException extends RuntimeException {
    public SuspensionEventPublishException(RuntimeException cause) {
        super("挂起已保存，但人工介入事件发布失败", cause);
    }
}
