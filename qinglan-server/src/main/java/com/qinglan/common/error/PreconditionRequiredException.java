package com.qinglan.common.error;

/**
 * 需要先满足前置条件才能继续（HTTP 428）。与 {@link BizException} 的唯一区别是**可携带业务数据**——
 * 「存在未读紧急公告」必须把公告内容带回前端以便直接弹窗，而 BizException 只存 ErrorCode。
 * 异常类型放在 common，业务模块只依赖 common，不产生反向依赖。
 */
public class PreconditionRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    /** transient：内容可能不可序列化，且 -Xlint:serial 会因非 transient 字段报警导致构建失败 */
    private final transient Object data;

    public PreconditionRequiredException(ErrorCode errorCode, Object data) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object data() {
        return data;
    }
}
