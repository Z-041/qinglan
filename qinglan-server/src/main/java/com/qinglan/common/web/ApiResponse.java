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

    /**
     * 携带业务数据的错误响应。仅用于「错误本身需要把数据带回前端」的场景——
     * 如 428 必须带上未读公告内容以便直接弹窗（计划 D3）。
     */
    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
