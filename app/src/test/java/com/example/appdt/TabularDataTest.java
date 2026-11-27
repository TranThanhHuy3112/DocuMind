package com.example.appdt;

import com.example.appdt.ui.tools.TabularData;
import org.junit.Test;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class TabularDataTest {
    private TabularData table(String csv) throws IOException { return TabularData.parse(new StringReader(csv)); }
    @Test public void arbitraryColumnsAndAllRowsContributeToStatistics() throws Exception {
        StringBuilder csv = new StringBuilder("Sản phẩm,Chi phí,Số lượng\n");
        for (int i = 0; i < 15; i++) csv.append("A,100,2\n");
        TabularData data = table(csv.toString());
        TabularData.Statistics stats = data.summarize(1, 0, TabularData.NumberStyle.DOT_DECIMAL);
        assertEquals(15, stats.count); assertEquals(12, stats.chart.size());
        assertEquals(0, new BigDecimal("1500").compareTo(stats.total));
        assertEquals(0, new BigDecimal("100").compareTo(stats.mean()));
        assertEquals(0, new BigDecimal("30").compareTo(data.summarize(2, 0, TabularData.NumberStyle.DOT_DECIMAL).total));
    }
    @Test public void decimalStylesAreExplicitAndNegativeValuesRemain() throws Exception {
        assertEquals(new BigDecimal("0.125"), TabularData.number("0.125", TabularData.NumberStyle.DOT_DECIMAL));
        assertEquals(new BigDecimal("1200.50"), TabularData.number("1.200,50 ₫", TabularData.NumberStyle.COMMA_DECIMAL));
        assertEquals(new BigDecimal("-25"), TabularData.number("(25)", TabularData.NumberStyle.DOT_DECIMAL));
        assertNull(TabularData.number("1,2,3", TabularData.NumberStyle.DOT_DECIMAL));
        TabularData.Statistics stats = table("Tên;Giá trị\nA;-10\nB;0\nC;20\nD;abc\nE;\n").summarize(1, 0, TabularData.NumberStyle.DOT_DECIMAL);
        assertEquals(3, stats.count); assertEquals(2, stats.skipped);
        assertEquals(new BigDecimal("10"), stats.total); assertEquals(new BigDecimal("-10"), stats.min);
    }
    @Test public void quotedMultilineAndEscapedQuotesPreserveCellBoundaries() throws Exception {
        TabularData data = table("\uFEFFTên,Giá\r\n\"Hàng, \"\"mới\"\"\nA\",1.25\r\n");
        assertEquals("Tên", data.headers.get(0)); assertEquals("Hàng, \"mới\"\nA", data.rows.get(0).get(0));
        assertEquals(new BigDecimal("1.25"), data.summarize(1,0,TabularData.NumberStyle.DOT_DECIMAL).total);
        assertEquals(2, table("Tên\tGiá\nA\t10\n").headers.size());
    }
    @Test public void malformedOversizeAndRaggedInputFailRatherThanReturningPartialTotals() throws Exception {
        StringBuilder rows = new StringBuilder("A,B\n");
        for (int i = 0; i < 10_001; i++) rows.append("x,1\n");
        assertThrows(IOException.class, () -> table(rows.toString()));
        assertThrows(IOException.class, () -> table("A,B\n\"unterminated,12\n"));
        assertThrows(IOException.class, () -> table("A,B\nx,1,2\n"));
        assertThrows(IOException.class, () -> table("A\n" + new String(new char[65537]).replace('\0', 'a')));
        assertNull(TabularData.number("NaN", TabularData.NumberStyle.DOT_DECIMAL));
        assertNull(TabularData.number("=SUM(A1:A2)", TabularData.NumberStyle.DOT_DECIMAL));
        assertNull(TabularData.number("1e999", TabularData.NumberStyle.DOT_DECIMAL));
    }
}
