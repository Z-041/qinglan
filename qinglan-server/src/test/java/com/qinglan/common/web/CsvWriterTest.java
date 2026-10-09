package com.qinglan.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 单元格格式化的测试：转义 + 公式注入防护。
 *
 * <p>注入防护是本项目单列的一条安全要求：Excel / WPS 会把以 {@code = + - @} 开头（或制表 / 回车 /
 * 换行开头）的单元格当公式求值，导出物里混入用户输入就可能形成「打开即执行」的链路。</p>
 */
class CsvWriterTest {

    @Test
    @DisplayName("公式注入防护：以 = + - @ 制表 回车 换行开头时前置单引号")
    void shouldNeutralizeFormulaInjection() {
        assertThat(CsvWriter.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvWriter.cell("+SUM(A1)")).isEqualTo("'+SUM(A1)");
        assertThat(CsvWriter.cell("-2+3")).isEqualTo("'-2+3");
        assertThat(CsvWriter.cell("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvWriter.cell("\tDDE")).isEqualTo("'\tDDE");
        // 回车 / 换行起点：既前置单引号，又因含换行被双引号包裹
        assertThat(CsvWriter.cell("\rX")).isEqualTo("\"'\rX\"");
        assertThat(CsvWriter.cell("\nX")).isEqualTo("\"'\nX\"");
    }

    @Test
    @DisplayName("普通值不加单引号")
    void shouldNotTouchPlainValues() {
        assertThat(CsvWriter.cell("hello")).isEqualTo("hello");
        assertThat(CsvWriter.cell("")).isEqualTo("");
        assertThat(CsvWriter.cell(null)).isEqualTo("");
        // 非首字符的公式符号不受影响
        assertThat(CsvWriter.cell("a=b")).isEqualTo("a=b");
    }

    @Test
    @DisplayName("含逗号 / 引号 / 换行时按 CSV 规则用双引号包裹并转义引号")
    void shouldEscapeCsvSpecialCharacters() {
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    @DisplayName("注入防护先于转义：带逗号的公式注入既前置单引号又加双引号")
    void shouldApplyInjectionBeforeQuoting() {
        assertThat(CsvWriter.cell("=A,B")).isEqualTo("\"'=A,B\"");
    }

    @Test
    @DisplayName("line() 用逗号连接各单元格")
    void shouldJoinCells() {
        assertThat(CsvWriter.line(1L, "POST", "a,b", null)).isEqualTo("1,POST,\"a,b\",");
    }
}
