package com.qinglan.review.repository;

import java.time.Instant;

/**
 * 一条已处置举报的「提交时刻 / 处置时刻」投影，用于计算处置时效（P50 / P90）。
 *
 * <p>为什么不直接在 SQL 里算分位：MySQL 8 没有 {@code PERCENTILE_CONT} 之类的分位函数，
 * 故只取两列原始时间戳，分位数在 Service 层用最近秩法计算；投影只有两列，抓取很轻。</p>
 */
public record ReportHandlingTime(Instant reportedAt, Instant handledAt) {
}
