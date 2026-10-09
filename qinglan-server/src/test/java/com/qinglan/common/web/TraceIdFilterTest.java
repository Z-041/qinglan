package com.qinglan.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 请求级 traceId：响应头回传、入站沿用、逐请求唯一，且日志 pattern 能渲染出 traceId。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private Logger filterLogger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;
    private PatternLayout layout;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);

        Context context = (Context) LoggerFactory.getILoggerFactory();
        // 与应用日志 pattern 中的 traceId 片段一致：%X{traceId:-}
        layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("%-5level [%X{traceId:-}] %msg%n");
        layout.start();

        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
        appender.stop();
        filterLogger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("响应头回传 X-Request-Id，且非空")
    void shouldReturnTraceIdHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("两次请求生成不同的 traceId")
    void shouldGenerateDistinctTraceIdPerRequest() throws Exception {
        String first = traceIdOfRequest();
        String second = traceIdOfRequest();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("入站自带 X-Request-Id 时原样沿用")
    void shouldHonorInboundTraceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/topics").header(TraceIdFilter.TRACE_ID_HEADER, "req-abc-123"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("req-abc-123");
    }

    @Test
    @DisplayName("入站 X-Request-Id 含非法字符时不采纳，改用生成的（防响应头注入）")
    void shouldRejectUnsafeInboundTraceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/topics").header(TraceIdFilter.TRACE_ID_HEADER, "bad value!"))
                .andExpect(status().isOk())
                .andReturn();

        String returned = result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(returned).isNotBlank().isNotEqualTo("bad value!");
    }

    @Test
    @DisplayName("日志里出现 traceId：pattern 渲染出与响应头一致的值")
    void shouldWriteTraceIdIntoLogs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/topics")).andReturn();
        String traceId = result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        String rendered = appender.list.stream()
                .map(event -> layout.doLayout(event))
                .collect(Collectors.joining());

        assertThat(rendered).contains(traceId);
    }

    private String traceIdOfRequest() throws Exception {
        return mockMvc.perform(get("/api/topics")).andReturn()
                .getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);
    }
}
