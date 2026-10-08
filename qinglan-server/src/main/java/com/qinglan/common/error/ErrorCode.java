package com.qinglan.common.error;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码。code 形如 QL-<HTTP 状态>-<序号>，便于日志检索与前端分支处理。
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "QL-400-001", "请求参数不合法"),
    EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "QL-400-002", "仅支持校园邮箱认证"),
    // 验证码「未获取 / 已过期 / 填错」统一用同一个码：区分开等于把「该邮箱是否请求过验证码」
    // 变成可探测的信息（T-12 I）
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "QL-400-003", "验证码不正确或已失效，请重新获取"),
    CONTACT_REQUIRED(HttpStatus.BAD_REQUEST, "QL-400-005", "该话题要求留下联系方式，便于对方联系到你"),
    UPLOAD_INVALID(HttpStatus.BAD_REQUEST, "QL-400-006", "仅支持 JPG / PNG / WebP，单张不超过 5MB"),
    CURSOR_INVALID(HttpStatus.BAD_REQUEST, "QL-400-007", "分页游标不合法"),
    CONTENT_REJECTED(HttpStatus.BAD_REQUEST, "QL-400-008", "内容暂不符合社区规范，请修改后重试"),
    // 隐私信息是「确定不该出现在校园墙」的一类，单独给码以便前端给出更具体的提示
    PRIVACY_CONTENT_BLOCKED(HttpStatus.BAD_REQUEST, "QL-400-009", "请勿填写身份证、银行卡号等敏感信息"),
    SELF_REPORT_FORBIDDEN(HttpStatus.BAD_REQUEST, "QL-400-010", "不能举报自己发布的内容"),
    // 不变量 #6：停留时长未达到公告要求，服务端拒绝标记已读（有效期证据不足）
    ANNOUNCEMENT_READ_TOO_EARLY(HttpStatus.BAD_REQUEST, "QL-400-011", "阅读时长不足，暂不能标记为已读"),
    // 不变量 #6 的另外两个条件（设计方案 5.2 的 AND）：需滚动到底、需勾选已了解
    ANNOUNCEMENT_READ_UNCONFIRMED(HttpStatus.BAD_REQUEST, "QL-400-012", "尚未完成强制阅读：需滚动到底并勾选已了解"),
    // P5 二期：提案的动作与目标当前状态不匹配（如对 PENDING 帖提「下架」、对可见帖提「维持下架」）
    PROPOSAL_TARGET_INVALID(HttpStatus.BAD_REQUEST, "QL-400-013", "目标当前状态与所选处置不匹配"),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "QL-401-001", "请先完成认证"),
    EMAIL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "QL-401-002", "邮箱认证凭证无效或已过期"),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "QL-403-001", "无权执行该操作"),
    USER_BANNED(HttpStatus.FORBIDDEN, "QL-403-002", "账号已被限制，暂时无法使用该功能"),
    NOT_CONTENT_OWNER(HttpStatus.FORBIDDEN, "QL-403-003", "只能操作自己发布的内容"),
    CREDIT_TOO_LOW(HttpStatus.FORBIDDEN, "QL-403-004", "信用分不足，暂时无法发帖"),
    // 「复审人 ≠ 原处置人」的落点：同一个管理员不能复核自己的裁决
    SELF_REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "QL-403-005", "不能复审自己处置过的内容"),
    // 唯一超管是后台的最后一道保险：被降级/封禁后没人能解锁，只能改库（P5 D4）
    SUPER_ADMIN_PROTECTED(HttpStatus.FORBIDDEN, "QL-403-006", "超级管理员账号不可变更角色，也不可被封禁"),
    ROLE_SELF_CHANGE_FORBIDDEN(HttpStatus.FORBIDDEN, "QL-403-007", "不能修改自己的角色"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-001", "用户不存在"),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-002", "帖子不存在或已被删除"),
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-003", "话题不存在"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-004", "评论不存在或已被删除"),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-005", "没有待处理的举报"),
    APPEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-006", "申诉不存在"),
    ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-007", "公告不存在"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-008", "通知不存在"),
    PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "QL-404-009", "处置提案不存在"),

    // 428：存在未读紧急公告，发帖/评论等写操作被前置拦截。响应需携带公告内容（PreconditionRequiredException）
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "QL-428-001", "存在未读的紧急公告"),

    EMAIL_ALREADY_BOUND(HttpStatus.CONFLICT, "QL-409-001", "该校园邮箱已完成认证，请直接登录"),
    REGISTRATION_CONFLICT(HttpStatus.CONFLICT, "QL-409-003", "该邮箱或学号已完成认证"),
    ILLEGAL_REVIEW_TRANSITION(HttpStatus.CONFLICT, "QL-409-004", "当前审核状态不允许该操作"),
    // 不变量 #4：重复举报明确回报，不静默吞掉——静默会让用户以为已提交
    ALREADY_REPORTED(HttpStatus.CONFLICT, "QL-409-005", "你已经举报过该内容，请等待处理"),
    // 每个目标只受理一次申诉（appeal.uk_appeal_target），重复提交明确回报
    APPEAL_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "QL-409-006", "该内容已提交过申诉，请等待复审"),
    APPEAL_WINDOW_EXPIRED(HttpStatus.CONFLICT, "QL-409-007", "申诉期已过，无法再提交申诉"),
    // 公告状态机（计划 D7）：仅 DRAFT 可发布、仅 PUBLISHED 可撤回，其余一律拒绝
    ANNOUNCEMENT_STATE_ILLEGAL(HttpStatus.CONFLICT, "QL-409-008", "公告当前状态不允许该操作"),
    // P5：委员会（管理员 + 评议员）合计上限 6 人，它是表决法定人数的基础（决策 D3）
    COMMITTEE_FULL(HttpStatus.CONFLICT, "QL-409-009", "管理员名额已满，请先收回其他成员的权限"),
    // P5 二期：同一目标同时只允许一条进行中提案（uk_proposal_open_target）
    PROPOSAL_ALREADY_OPEN(HttpStatus.CONFLICT, "QL-409-010", "该内容已有进行中的处置提案，请等待表决结果"),
    // P5 二期：一人一票（uk_vote_proposal_voter），重复投票明确回报，不静默吞掉
    ALREADY_VOTED(HttpStatus.CONFLICT, "QL-409-011", "你已经投过票"),

    CODE_SEND_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "QL-429-001", "验证码发送过于频繁，请稍后再试"),
    VERIFY_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "QL-429-002", "验证失败次数过多，请重新获取验证码"),
    UPLOAD_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "QL-429-003", "上传过于频繁，请稍后再试"),
    ANNOUNCEMENT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "QL-429-004", "今日公告发布已达上限"),
    // 标记已读是匿名开放的写接口（游客也要能被强制阅读），按设备限流防止被刷表
    ANNOUNCEMENT_READ_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "QL-429-005", "操作过于频繁，请稍后再试"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "QL-500-001", "服务暂时不可用"),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "QL-500-002", "文件存储暂时不可用，请稍后重试");

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
