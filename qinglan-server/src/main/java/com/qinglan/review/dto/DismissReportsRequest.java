package com.qinglan.review.dto;

import com.qinglan.review.entity.ReviewTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 驳回举报：内容不违规。
 *
 * <p>按**目标**而非单条举报 id 驳回——队列是以目标聚合的，管理员点的是「这个内容没问题」，
 * 理应连同该目标下所有待处理举报一起结案，否则剩几条还挂在队列里，等于没处理干净。</p>
 */
public record DismissReportsRequest(
        @NotNull(message = "请选择举报对象类型")
        ReviewTargetType targetType,

        @NotNull(message = "请选择举报对象")
        @Positive(message = "举报对象不合法")
        Long targetId,

        @Size(max = 500, message = "驳回说明不能超过 500 字")
        String reason
) {
}
