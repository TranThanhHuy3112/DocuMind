package com.example.appdt.ui.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RevenueCsvParser {
    private static final int MAX_LINE_LENGTH = 64 * 1024;
    private static final int MAX_ROWS_SCANNED = 10_000;
    private static final int MAX_COLUMNS = 256;

    public static final class Entry {
        public final String label;
        public final double value;
        Entry(String label, double value) { this.label = label; this.value = value; }
    }

    private RevenueCsvParser() { }

    public static List<Entry> parse(Reader reader) throws IOException {
        BufferedReader input = new BufferedReader(reader);
        String headerLine = readBoundedLine(input);
        List<Entry> result = new ArrayList<>();
        if (headerLine == null) return result;
        char delimiter = delimiter(headerLine);
        List<String> headers = cells(headerLine, delimiter);
        int valueColumn = -1;
        int labelColumn = -1;
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.get(i).replace("\uFEFF", "").trim()
                    .toLowerCase(Locale.ROOT);
            if (name.equals("revenue") || name.equals("doanh thu")) valueColumn = i;
            if (name.equals("month") || name.equals("tháng") || name.equals("period")) labelColumn = i;
        }
        if (valueColumn < 0) return result;
        if (labelColumn < 0) labelColumn = valueColumn == 0 && headers.size() > 1 ? 1 : 0;
        String line;
        int rowsScanned = 0;
        while (result.size() < 12 && rowsScanned < MAX_ROWS_SCANNED
                && (line = readBoundedLine(input)) != null) {
            rowsScanned++;
            List<String> row = cells(line, delimiter);
            if (row.size() <= Math.max(valueColumn, labelColumn)) continue;
            try {
                double value = parseAmount(row.get(valueColumn));
                String label = row.get(labelColumn).trim();
                if (!label.isEmpty() && Double.isFinite(value) && value >= 0) {
                    result.add(new Entry(label, value));
                }
            } catch (NumberFormatException ignored) {
                // Skip rows that do not contain a numeric revenue value.
            }
        }
        return result;
    }

    private static String readBoundedLine(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch = reader.read();
        if (ch == -1) return null;
        while (ch != -1) {
            if (ch == '\n') break;
            if (ch == '\r') {
                reader.mark(1);
                if (reader.read() != '\n') reader.reset();
                break;
            }
            if (sb.length() >= MAX_LINE_LENGTH) {
                throw new IOException("Dòng CSV vượt quá độ dài cho phép.");
            }
            sb.append((char) ch);
            ch = reader.read();
        }
        return sb.toString();
    }

    private static char delimiter(String header) {
        int commas = 0, semicolons = 0;
        boolean quoted = false;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && c == ',') commas++;
            else if (!quoted && c == ';') semicolons++;
        }
        return semicolons > commas ? ';' : ',';
    }

    private static double parseAmount(String raw) {
        String value = raw.trim().replace("\u00A0", "").replace(" ", "")
                .replace("VNĐ", "").replace("vnđ", "")
                .replace("VND", "").replace("vnd", "")
                .replace("₫", "").replace("đ", "").replace("Đ", "");
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) value = value.replace(".", "").replace(',', '.');
            else value = value.replace(",", "");
        } else if (comma >= 0) {
            int decimals = value.length() - comma - 1;
            value = decimals == 3 ? value.replace(",", "")
                    : value.replace(',', '.');
        } else if (dot >= 0 && value.length() - dot - 1 == 3) {
            value = value.replace(".", "");
        }
        return Double.parseDouble(value);
    }

    private static List<String> cells(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"'); i++;
                } else quoted = !quoted;
            } else if (character == delimiter && !quoted) {
                result.add(cell.toString()); cell.setLength(0);
                if (result.size() >= MAX_COLUMNS) break;
            } else cell.append(character);
        }
        if (result.size() < MAX_COLUMNS) result.add(cell.toString());
        return result;
    }
}
