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
