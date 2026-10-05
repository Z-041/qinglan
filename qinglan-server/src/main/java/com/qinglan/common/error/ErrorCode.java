package com.qinglan.common.error;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码。code 形如 QL-<HTTP 状态>-<序号>，便于日志检索与前端分支处理。
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "QL-400-001", "请求参数不合法"),
    EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "QL-400-002", "仅支持校园邮箱认证"),
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "QL-400-003", "验证码不正确"),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "QL-400-004", "验证码已失效，请重新获取"),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "QL-401-001", "请先完成认证"),
    EMAIL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "QL-401-002", "邮箱认证凭证无效或已过期"),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "QL-403-001", "无权执行该操作"),
    USER_BANNED(HttpStatus.FORBIDDEN, "QL-403-002", "账号已被限制，暂时无法使用该功能"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-001", "用户不存在"),

    EMAIL_ALREADY_BOUND(HttpStatus.CONFLICT, "QL-409-001", "该校园邮箱已完成认证，请直接登录"),
    STUDENT_ID_ALREADY_BOUND(HttpStatus.CONFLICT, "QL-409-002", "该学号已被绑定，不可重复认证"),
    REGISTRATION_CONFLICT(HttpStatus.CONFLICT, "QL-409-003", "该邮箱或学号已完成认证"),

    CODE_SEND_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "QL-429-001", "验证码发送过于频繁，请稍后再试"),
    VERIFY_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "QL-429-002", "验证失败次数过多，请重新获取验证码"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "QL-500-001", "服务暂时不可用");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
