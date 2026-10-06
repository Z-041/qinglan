package com.qinglan.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiResponse} 的 error 重载语义：默认 data 为 null，带 data 的重载原样保留。
 * 428 依赖后者把公告快照带回前端（计划 D3）。
 */
class ApiResponseTest {

    @Test
    @DisplayName("error(code, message) 的 data 恒为 null")
    void shouldKeepDataNullForTwoArgError() {
        ApiResponse<Void> response = ApiResponse.error("QL-400-001", "请求参数不合法");

        assertThat(response.code()).isEqualTo("QL-400-001");
        assertThat(response.message()).isEqualTo("请求参数不合法");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("error(code, message, data) 原样保留 data")
    void shouldPreserveDataForThreeArgError() {
        Map<String, Object> payload = Map.of("id", 12L, "title", "紧急公告");

        ApiResponse<Object> response = ApiResponse.error("QL-428-001", "存在未读的紧急公告", payload);

        assertThat(response.code()).isEqualTo("QL-428-001");
        assertThat(response.message()).isEqualTo("存在未读的紧急公告");
        assertThat(response.data()).isSameAs(payload);
    }
}
