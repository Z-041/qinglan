package com.qinglan.common.web;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * CSV 单元格格式化：转义 + <b>公式注入防护</b>。
 *
 * <p>为什么必须做注入防护：Excel / WPS 打开 CSV 时会把以 {@code = + - @} 开头的单元格
 * 当作公式求值（{@code =1+1} 会算出 2，{@code =HYPERLINK(...)}、{@code =cmd|...} 更危险），
 * 制表符 / 回车开头的值也可能是命令触发的铺垫。表里的「理由」「筛选参数」等文本来自用户输入，
 * 一旦原样导出，就成了「管理员打开导出的文件即执行」的攻击链。业界通行做法是在这类首字符前
 * 前置一个单引号，让 Excel 按文本处理。</p>
 *
 * <p>放在 {@code common.web}：审计导出（review）与用户导出（user）都要用，属跨模块共享的无业务
 * 底座；本类不依赖任何业务模块，方向与 AGENTS.md 第三章一致。</p>
 */
public final class CsvWriter {

    /** Excel 兼容中文所需的 UTF-8 BOM，必须作为文件首字节写出，否则 Excel 会把中文显示成乱码 */
    public static final String UTF8_BOM = "\uFEFF";

    /** 会触发电子表格公式解释的首字符（含制表 / 回车 / 换行） */
    private static final String DANGEROUS_PREFIX = "=+-@\t\r\n";

    /** 单次导出行数上限：超限明确报错，绝不静默截断 */
    public static final int DEFAULT_ROW_LIMIT = 5000;

    private CsvWriter() {
    }

    /**
     * 把单个值格式化为 CSV 单元格：先做注入防护，再做标准转义（逗号 / 引号 / 换行）。
     *
     * <p>顺序不能反：先补单引号再套双引号，单引号才会留在单元格内容里起到「按文本处理」的作用。</p>
     */
    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof String string ? string : String.valueOf(value);
        if (!text.isEmpty() && DANGEROUS_PREFIX.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            text = '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /** 由若干单元格拼成一行（不含换行符），各值按 {@link #cell(Object)} 格式化 */
    public static String line(Object... values) {
        return Arrays.stream(values).map(CsvWriter::cell).collect(Collectors.joining(","));
    }
}
