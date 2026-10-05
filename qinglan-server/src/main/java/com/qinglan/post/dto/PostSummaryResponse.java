package com.qinglan.post.dto;

import java.time.Instant;
import java.util.List;

/**
 * 信息流列表项。
 *
 * <p>刻意不含 {@code contact} 与 {@code authorId}：</p>
 * <ul>
 *   <li>联系方式只在详情页按 viewer 身份返回，列表不批量下发，既减少抓取面也省去逐帖写审计日志</li>
 *   <li>不变量 #1：对外接口不得同时返回 anonymous_code 与 author_id，故只给代号，
 *       「是否本人」用 {@link #mine()} 表达</li>
 * </ul>
 *
 * @param mine 是否为当前访问者本人所发，供前端展示删除入口用
 */
public record PostSummaryResponse(
        Long id,
        Long topicId,
        String topicName,
        String content,
        List<String> images,
        String anonymousCode,
        long likeCount,
        boolean liked,
        int commentCount,
        boolean mine,
        Instant createdAt
) {
}
