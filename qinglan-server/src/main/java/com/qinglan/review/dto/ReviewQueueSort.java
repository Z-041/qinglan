package com.qinglan.review.dto;

/**
 * 审核队列排序口径。
 *
 * <p>两种排序的**游标格式不同**（默认排序是「举报数:首报微秒:目标 id:类型序」四元组，
 * 首报升序是「首报微秒:目标 id:类型序」三元组）。前端切换排序时必须一并回传 {@code sort}，
 * 服务端据此解析游标；否则会按错误的段数解析而漏行或重复。</p>
 */
public enum ReviewQueueSort {

    /** 默认：举报数降序、首报时间升序——与既有行为完全一致 */
    REPORT_COUNT_DESC,

    /** 首报时间升序（等得最久的先处理），供「超时优先」开关使用 */
    FIRST_REPORTED_ASC
}
