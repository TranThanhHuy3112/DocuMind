package com.example.appdt.ui.tools;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OfficeTextExtractorTest {
    @Test public void extractsDocumentParagraphs() throws Exception {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("word/document.xml", "<w:document xmlns:w=\"urn:w\"><w:body>"
                + "<w:p><w:r><w:t>Xin chào</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>Thế giới</w:t></w:r></w:p>"
                + "</w:body></w:document>");
        assertEquals("Xin chào\nThế giới", OfficeTextExtractor.docx(zip(parts)));
    }

    @Test public void extractsSpreadsheetForRevenueChart() throws Exception {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("xl/sharedStrings.xml", "<sst><si><t>month</t></si>"
                + "<si><t>revenue</t></si><si><t>January</t></si></sst>");
        parts.put("xl/worksheets/sheet1.xml", "<worksheet><sheetData>"
                + "<row><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                + "<row><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>1200</v></c></row>"
                + "</sheetData></worksheet>");
        String csv = OfficeTextExtractor.xlsxCsv(zip(parts));
        assertTrue(csv.contains("month,revenue"));
        assertEquals(1200, RevenueCsvParser.parse(new StringReader(csv)).get(0).value, 0.001);
    }

    @Test public void wordTablePreservesColumnsAndDecimalValues() throws Exception {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("word/document.xml", "<w:document xmlns:w=\"urn:w\"><w:body><w:tbl>"
                + "<w:tr><w:tc><w:p><w:r><w:t>Sản phẩm</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>Giá</w:t></w:r></w:p></w:tc></w:tr>"
                + "<w:tr><w:tc><w:p><w:r><w:t>A,B</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>-0.125</w:t></w:r></w:p></w:tc></w:tr>"
                + "</w:tbl></w:body></w:document>");
        TabularData table = TabularData.parse(new StringReader(OfficeTextExtractor.docxTableCsv(zip(parts))));
        assertEquals("A,B", table.rows.get(0).get(0));
        assertEquals(-0.125, table.summarize(1, 0, TabularData.NumberStyle.DOT_DECIMAL).total.doubleValue(), 0.00001);
    }

    @Test public void mergedOrOmittedWordCellsFailInsteadOfShiftingValues() throws Exception {
        for (String property : new String[]{"<w:gridBefore w:val=\"1\"/>", "<w:gridSpan w:val=\"2\"/>"}) {
            Map<String, String> parts = new LinkedHashMap<>();
            parts.put("word/document.xml", "<w:document xmlns:w=\"urn:w\"><w:body><w:tbl><w:tr>"
                    + property + "<w:tc><w:p><w:r><w:t>10</w:t></w:r></w:p></w:tc>"
                    + "</w:tr></w:tbl></w:body></w:document>");
            org.junit.Assert.assertThrows(java.io.IOException.class, () -> OfficeTextExtractor.docxTableCsv(zip(parts)));
        }
    }

    @Test public void strictExcelAnalysisDoesNotChangeAiExtractionOfMergedTitles() throws Exception {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("xl/worksheets/sheet1.xml", "<worksheet><sheetData><row><c r=\"A1\" t=\"inlineStr\">"
                + "<is><t>Title</t></is></c></row></sheetData><mergeCells><mergeCell ref=\"A1:B1\"/></mergeCells></worksheet>");
        byte[] archive = zip(parts);
        assertTrue(OfficeTextExtractor.xlsxCsv(archive).contains("Title"));
        org.junit.Assert.assertThrows(java.io.IOException.class, () -> OfficeTextExtractor.xlsxAnalysisCsv(archive));
    }

    private static byte[] zip(Map<String, String> parts) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                archive.putNextEntry(new ZipEntry(part.getKey()));
                archive.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
