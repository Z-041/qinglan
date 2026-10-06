package com.qinglan.common.web;

import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.error.PreconditionRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 428 专家分支：{@link PreconditionRequiredException} 经全局异常处理映射为 HTTP 428，
 * 且 body 携带公告数据（计划 D3）。此处直接单测 handler 方法，不额外拉起集成上下文。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("PreconditionRequiredException 映射为 428，且 body.data 保留公告快照")
    void shouldMapPreconditionRequiredTo428WithData() {
        Map<String, Object> announcement = Map.of("id", 12L, "title", "紧急公告");

        ResponseEntity<ApiResponse<Object>> response =
                handler.handlePrecondition(new PreconditionRequiredException(
                        ErrorCode.PRECONDITION_REQUIRED, announcement));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.PRECONDITION_REQUIRED.code());
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.PRECONDITION_REQUIRED.message());
        assertThat(response.getBody().data()).isSameAs(announcement);
    }
}
