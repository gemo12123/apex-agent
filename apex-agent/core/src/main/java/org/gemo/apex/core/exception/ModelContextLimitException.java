package org.gemo.apex.core.exception;

public final class ModelContextLimitException extends RuntimeException {
    public ModelContextLimitException(long actual, long limit) {
        super("模型请求超过硬上限: actual=" + actual + ", limit=" + limit);
    }
}
