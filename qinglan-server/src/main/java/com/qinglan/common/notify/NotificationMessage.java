package com.qinglan.common.notify;

/**
 * 通知内容快照。
 *
 * @param type  通知类型
 * @param title 标题，非空
 * @param body  正文摘要，可为 null
 * @param link  前端跳转路径（如 {@code /posts/12}），可为 null
 */
public record NotificationMessage(NotificationType type, String title, String body, String link) {
}
