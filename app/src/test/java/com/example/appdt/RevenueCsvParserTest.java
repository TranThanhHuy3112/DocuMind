package com.example.appdt;

import com.example.appdt.ui.tools.RevenueCsvParser;

import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RevenueCsvParserTest {
    @Test public void parsesQuotedLabelsAndSkipsInvalidRevenue() throws Exception {
        List<RevenueCsvParser.Entry> entries = RevenueCsvParser.parse(new StringReader(
                "month,revenue\n\"January, first\",1200\nFebruary,not-a-number\nMarch,1380\n"));
        assertEquals(2, entries.size());
        assertEquals("January, first", entries.get(0).label);
        assertEquals(1200.0, entries.get(0).value, 0.001);
        assertEquals(1380.0, entries.get(1).value, 0.001);
    }

    @Test public void missingRevenueColumnReturnsEmptyData() throws Exception {
        assertTrue(RevenueCsvParser.parse(new StringReader(
                "month,expense\nJanuary,50\n")).isEmpty());
    }

    @Test public void parsesVietnameseSemicolonAndMoneyValues() throws Exception {
        List<RevenueCsvParser.Entry> entries = RevenueCsvParser.parse(new StringReader(
                "\uFEFFtháng;doanh thu\nMột;1.200,50 ₫\nHai;2.000\nBa;300.000 VNĐ\nBốn;400.000Đ\n"));
        assertEquals(4, entries.size());
        assertEquals(1200.5, entries.get(0).value, 0.001);
        assertEquals(2000, entries.get(1).value, 0.001);
        assertEquals(300000, entries.get(2).value, 0.001);
        assertEquals(400000, entries.get(3).value, 0.001);
    }
}
