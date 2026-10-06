package com.qinglan.common.notify;

/**
 * 通知端口。
 *
 * <p>与 {@code common.audit.AuditLogService} 同一手法：把「发出通知」这一跨模块能力下沉到 common，
 * 业务模块（post / comment / review）只依赖本接口，实现放在 notification 模块。若让业务模块直接依赖
 * notification，会新增多条业务→业务边且语义上反向；下沉到 common 后依赖图仍是「业务模块 → common」。</p>
 */
public interface NotificationPort {

    /**
     * 发出一条通知。接收者与触发者是同一人时**静默跳过**（自己评论/点赞自己的帖子不产生通知），
     * 该判断集中在实现里，调用方不必各自处理。
     *
     * @param recipientId 接收者 userId
     * @param actorId     触发者 userId；自触发时不发通知
     * @param message     通知内容快照
     */
    void notify(Long recipientId, Long actorId, NotificationMessage message);
}
