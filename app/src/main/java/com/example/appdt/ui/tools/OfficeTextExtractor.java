package com.example.appdt.ui.tools;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extracts readable text from Office Open XML files without uploading the binary package. */
final class OfficeTextExtractor {
    private static final int MAX_XML_BYTES = 8 * 1024 * 1024;

    private OfficeTextExtractor() { }

    static String docx(byte[] archive) throws IOException {
        Map<String, byte[]> parts = entries(archive);
        byte[] body = parts.get("word/document.xml");
        if (body == null) throw new IOException("Tệp DOCX không có nội dung văn bản.");
        StringBuilder text = new StringBuilder();
        try {
            XmlPullParser parser = xml(body);
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                if (parser.getName().equals("p") && text.length() > 0) text.append('\n');
                if (parser.getName().equals("t")) text.append(parser.nextText());
                if (text.length() > MAX_XML_BYTES) throw new IOException("Nội dung DOCX quá dài.");
            }
        } catch (XmlPullParserException error) {
            throw new IOException("Không thể đọc nội dung DOCX.", error);
        }
        return text.toString();
    }

    static String xlsxCsv(byte[] archive) throws IOException {
        Map<String, byte[]> parts = entries(archive);
        byte[] sheet = parts.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            int first = Integer.MAX_VALUE;
            for (Map.Entry<String, byte[]> part : parts.entrySet()) {
                String name = part.getKey();
                if (!name.matches("xl/worksheets/sheet[0-9]+\\.xml")) continue;
                try {
                    int number = Integer.parseInt(name.substring("xl/worksheets/sheet".length(),
                            name.length() - ".xml".length()));
                    if (number < first) { first = number; sheet = part.getValue(); }
                } catch (NumberFormatException ignored) { }
            }
        }
        if (sheet == null) throw new IOException("Tệp XLSX không có bảng dữ liệu đầu tiên.");
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        StringBuilder csv = new StringBuilder();
        try {
            XmlPullParser parser = xml(sheet);
            Map<Integer, String> row = new HashMap<>();
            int column = -1;
            int maxColumn = -1;
            boolean sharedCell = false;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if (name.equals("row")) {
                        row.clear(); maxColumn = -1;
                    } else if (name.equals("c")) {
                        column = columnIndex(parser.getAttributeValue(null, "r"));
                        sharedCell = "s".equals(parser.getAttributeValue(null, "t"));
                    } else if ((name.equals("v") || name.equals("t")) && column >= 0) {
                        String value = parser.nextText();
                        if (sharedCell && name.equals("v")) {
                            try {
                                int index = Integer.parseInt(value);
                                value = index >= 0 && index < shared.size() ? shared.get(index) : "";
                            } catch (NumberFormatException ignored) { value = ""; }
                        }
                        row.put(column, value);
                        maxColumn = Math.max(maxColumn, column);
                    }
                } else if (event == XmlPullParser.END_TAG && parser.getName().equals("row")) {
                    if (maxColumn >= 0) {
                        if (csv.length() > 0) csv.append('\n');
                        for (int i = 0; i <= Math.min(maxColumn, 100); i++) {
                            if (i > 0) csv.append(',');
                            csv.append(quote(row.getOrDefault(i, "")));
                        }
                    }
                    if (csv.length() > MAX_XML_BYTES) throw new IOException("Bảng XLSX quá lớn.");
                }
            }
        } catch (XmlPullParserException error) {
            throw new IOException("Không thể đọc bảng XLSX.", error);
        }
        return csv.toString();
    }

    private static List<String> sharedStrings(byte[] xml) throws IOException {
        List<String> values = new ArrayList<>();
        if (xml == null) return values;
        try {
            XmlPullParser parser = xml(xml);
            StringBuilder item = null;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.getName().equals("si")) {
                    item = new StringBuilder();
                } else if (event == XmlPullParser.START_TAG && parser.getName().equals("t")
                        && item != null) {
                    item.append(parser.nextText());
                } else if (event == XmlPullParser.END_TAG && parser.getName().equals("si")
                        && item != null) {
                    values.add(item.toString()); item = null;
                }
            }
        } catch (XmlPullParserException error) {
            throw new IOException("Không thể đọc chuỗi trong XLSX.", error);
        }
        return values;
    }

    private static Map<String, byte[]> entries(byte[] archive) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.equals("word/document.xml") && !name.equals("xl/sharedStrings.xml")
                        && !name.matches("xl/worksheets/sheet[0-9]+\\.xml")) continue;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    if (output.size() + count > MAX_XML_BYTES
                            || total + output.size() + count > MAX_XML_BYTES * 2)
                        throw new IOException("Nội dung Office quá lớn.");
                    output.write(buffer, 0, count);
                }
                result.put(name, output.toByteArray());
                total += output.size();
            }
        }
        return result;
    }

    private static XmlPullParser xml(byte[] bytes) throws XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(bytes), "UTF-8");
        return parser;
    }

    private static int columnIndex(String reference) {
        if (reference == null) return -1;
        int result = 0;
        for (int i = 0; i < reference.length(); i++) {
            char c = Character.toUpperCase(reference.charAt(i));
            if (c < 'A' || c > 'Z') break;
            result = result * 26 + c - 'A' + 1;
        }
        return result - 1;
    }

    private static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0)
            return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
