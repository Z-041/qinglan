package com.qinglan.review.dto;

import com.qinglan.review.entity.ArbitrationDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 超管裁决（P5 方案 5.4）。
 *
 * <p>裁决是单人动作（超管本就是兜底），但**必须填理由**：它推翻了委员会的表决，
 * 不写理由就无法追溯「为什么这么判」。</p>
 *
 * @param reason 裁决理由，写入提案结论与审计（{@code PROPOSAL_RESOLVE}）
 */
public record ArbitrateRequest(
        @NotNull(message = "请选择裁决结果")
        ArbitrationDecision decision,

        @NotBlank(message = "请填写裁决理由")
        @Size(max = 200, message = "裁决理由不能超过 200 字")
        String reason
) {
}
