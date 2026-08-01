package org.gemo.apex.common.exception;

public final class JsonEncodingException extends RuntimeException {
    public JsonEncodingException(Class<?> type, Throwable cause) {
        super("JSON 序列化失败，类型: " + type.getName(), cause);
    }
}
