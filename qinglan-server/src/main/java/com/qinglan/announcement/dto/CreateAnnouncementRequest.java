package com.qinglan.announcement.dto;

import com.qinglan.announcement.entity.AnnouncementLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 建公告草稿的入参。
 *
 * @param minReadSeconds 可为空；为空时取配置默认值 {@code qinglan.announcement.min-read-seconds}
 * @param supersedesId   更正前的公告 id，非空表示本条是更正
 */
public record CreateAnnouncementRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题不超过 120 字")
        String title,

        @NotBlank(message = "正文不能为空")
        @Size(max = 5000, message = "正文不超过 5000 字")
        String content,

        @NotNull(message = "公告级别不能为空")
        AnnouncementLevel level,

        @NotNull(message = "生效开始时间不能为空")
        Instant startAt,

        Instant endAt,

        @Min(value = 0, message = "最少阅读秒数不能为负")
        @Max(value = 60, message = "最少阅读秒数不超过 60 秒")
        Integer minReadSeconds,

        boolean requireScrollToEnd,

        boolean requireConfirm,

        Long supersedesId
) {

    /**
     * 结束时间必须晚于开始时间。
     *
     * <p>否则草稿能正常发布、管理端看起来也「已发布」，但生效判定
     * （{@code startAt <= now < endAt}）永远为假，用户侧一条都收不到——属于静默失效，
     * 事后很难归因。跨字段约束放在 DTO：它属参数校验范畴，不该等到业务层才发现。</p>
     */
    @AssertTrue(message = "结束时间必须晚于开始时间")
    public boolean isTimeRangeValid() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }
}
