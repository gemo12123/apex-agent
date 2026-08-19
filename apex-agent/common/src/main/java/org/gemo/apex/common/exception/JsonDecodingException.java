package org.gemo.apex.common.exception;

public class JsonDecodingException extends RuntimeException {
    public JsonDecodingException(String targetType, Throwable cause) {
        super("JSON 反序列化失败，目标类型: " + targetType, cause);
    }
}
