package com.qinglan.common.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CSV 下载响应的公共头部设置（审计导出 / 用户导出共用）。
 *
 * <p>集中一处，保证两端导出的 {@code Content-Type} 与文件名格式一致：{@code text/csv; charset=UTF-8}
 * 配合正文首字节的 UTF-8 BOM（见 {@link CsvWriter#UTF8_BOM}）才是 Excel 能正确识别中文的组合——
 * 只有 BOM 没有 charset、或反之，都可能让 Excel 按本地编码解析而乱码。</p>
 *
 * <p>文件名带 Asia/Shanghai 时间戳，便于多次导出并存且不互相覆盖；文件名用 ASCII，
 * 避免 Content-Disposition 的非 ASCII 编码问题。</p>
 */
public final class CsvDownload {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Shanghai"));

    private CsvDownload() {
    }

    /**
     * 设置下载相关响应头（不提交响应，正文仍由调用方流式写出）。
     *
     * @param baseName 文件名前缀，如 {@code audit-logs} / {@code users}
     */
    public static void prepare(HttpServletResponse response, String baseName) {
        String fileName = baseName + "-" + FILE_TIMESTAMP.format(Instant.now()) + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
    }
}
