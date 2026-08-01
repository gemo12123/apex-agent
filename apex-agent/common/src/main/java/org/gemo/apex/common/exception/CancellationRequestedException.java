package org.gemo.apex.common.exception;

public final class CancellationRequestedException extends RuntimeException {
    public CancellationRequestedException() {
        super("请求已取消");
    }
}
