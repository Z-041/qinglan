package com.qinglan.common.web;

/**
 * 统一响应体。data 为 null 时不参与序列化判断由前端按 code 处理。
 */
public record ApiResponse<T>(String code, String message, T data) {

    private static final String SUCCESS_CODE = "QL-000-000";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(SUCCESS_CODE, "ok", null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
