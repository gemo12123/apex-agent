package org.gemo.apex.platform.web;

public record ApiResponse<T>(int code, T data, String message) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(200, data, "success"); }
}
