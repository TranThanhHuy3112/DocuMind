package com.example.appdt.ui.tools;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded table import and deterministic statistics; never evaluates cell formulas. */
public final class TabularData {
    public static final class ChartEntry {
        public final String label;
        public final double value;
        ChartEntry(String label, double value) { this.label = label; this.value = value; }
    }
    public enum NumberStyle { DOT_DECIMAL, COMMA_DECIMAL }
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_COLUMNS = 256;
    private static final int MAX_RECORD = 64 * 1024;
    private static final int MAX_CHARS = 8 * 1024 * 1024;
    private static final Pattern CURRENCY_PREFIX = Pattern.compile("(?i)^(VND|VNĐ|[₫$€])\\s*");
    private static final Pattern CURRENCY_SUFFIX = Pattern.compile("(?i)\\s*(VND|VNĐ|[₫đĐ$€])$");
    private static final Pattern DOT_NUMBER = numberPattern(",", "\\.");
    private static final Pattern COMMA_NUMBER = numberPattern("\\.", ",");
    public final List<String> headers;
    public final List<List<String>> rows;

    private TabularData(List<String> headers, List<List<String>> rows) {
        this.headers = Collections.unmodifiableList(headers);
        this.rows = Collections.unmodifiableList(rows);
    }

    private static Pattern numberPattern(String group, String decimal) {
        return Pattern.compile("[+-]?(?:[0-9]+|[0-9]{1,3}(?:" + group + "[0-9]{3})+)(?:" + decimal + "[0-9]+)?(?:[eE][+-]?[0-9]{1,3})?");
    }

    public static TabularData parse(Reader source) throws IOException {
        PushbackReader input = new PushbackReader(source, 1);
        String record = record(input);
        while (record != null && record.trim().isEmpty()) record = record(input);
        if (record == null) throw new IOException("Tệp không có bảng dữ liệu.");
        record = record.replace("\uFEFF", "");
        char separator = delimiter(record);
        List<String> headers = cells(record, separator);
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.get(i).trim();
            headers.set(i, name.isEmpty() ? "Cột " + (i + 1) : name);
        }
        List<List<String>> rows = new ArrayList<>();
        int chars = record.length();
        while ((record = record(input)) != null) {
            chars += record.length();
            if (chars > MAX_CHARS) throw new IOException("Bảng vượt giới hạn 8 MB nội dung.");
            if (record.trim().isEmpty()) continue;
            if (rows.size() >= MAX_ROWS) throw new IOException("Bảng vượt 10.000 dòng; hãy chia nhỏ tệp. Chưa tính tổng để tránh thiếu dữ liệu.");
            List<String> row = cells(record, separator);
            if (row.size() > headers.size()) throw new IOException("Số cột không khớp tiêu đề ở dòng " + (rows.size() + 2) + ".");
            while (row.size() < headers.size()) row.add("");
            rows.add(Collections.unmodifiableList(row));
        }
        return new TabularData(headers, rows);
    }

    private static String record(PushbackReader input) throws IOException {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        int c;
        while ((c = input.read()) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Đã hủy đọc bảng.");
            if (!quoted && (c == '\n' || c == '\r')) {
                if (c == '\r') { int next = input.read(); if (next != '\n' && next != -1) input.unread(next); }
                return out.toString();
            }
            if (out.length() >= MAX_RECORD) throw new IOException("Một dòng bảng vượt 64 KB.");
            out.append((char) c);
            if (c == '"') quoted = !quoted;
        }
        if (quoted) throw new IOException("Ô CSV thiếu dấu đóng ngoặc kép.");
        return out.length() == 0 ? null : out.toString();
    }

    private static char delimiter(String record) {
        int[] counts = new int[3];
        char[] candidates = {',', ';', '\t'};
        boolean quoted = false;
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (c == '"') quoted = !quoted;
            if (!quoted) for (int k = 0; k < candidates.length; k++) if (c == candidates[k]) counts[k]++;
        }
        int best = 0;
        for (int k = 1; k < counts.length; k++) if (counts[k] > counts[best]) best = k;
        return candidates[best];
    }

    private static List<String> cells(String record, char delimiter) throws IOException {
        List<String> cells = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < record.length() && record.charAt(i + 1) == '"') { value.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else value.append(c);
            } else if (c == delimiter) {
                cells.add(value.toString()); value.setLength(0); closed = false;
                if (cells.size() >= MAX_COLUMNS) throw new IOException("Bảng vượt 256 cột.");
            } else if (c == '"') {
                if (closed || value.toString().trim().length() > 0) throw new IOException("Dấu ngoặc kép CSV không hợp lệ.");
                value.setLength(0); quoted = true;
            } else {
                if (closed && !Character.isWhitespace(c)) throw new IOException("Nội dung sau ô CSV có ngoặc kép không hợp lệ.");
                if (!closed) value.append(c);
            }
        }
        if (quoted) throw new IOException("Ô CSV thiếu dấu đóng ngoặc kép.");
        cells.add(value.toString());
        return cells;
    }

    public static BigDecimal number(String raw, NumberStyle style) {
        if (raw.length() > 256) return null;
        String value = raw.trim().replace('\u2212', '-').replace('\u00A0', ' ');
        value = CURRENCY_PREFIX.matcher(value).replaceFirst("");
        value = CURRENCY_SUFFIX.matcher(value).replaceFirst("").trim();
        boolean parentheses = value.startsWith("(") && value.endsWith(")");
        if (parentheses) value = value.substring(1, value.length() - 1);
        Pattern number = style == NumberStyle.DOT_DECIMAL ? DOT_NUMBER : COMMA_NUMBER;
        if (!number.matcher(value).matches()) return null;
        if (parentheses && (value.startsWith("-") || value.startsWith("+"))) return null;
        value = style == NumberStyle.DOT_DECIMAL ? value.replace(",", "") : value.replace(".", "").replace(',', '.');
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.precision() > 100 || Math.abs(amount.scale()) > 100) return null;
            return parentheses ? amount.negate() : amount;
        } catch (NumberFormatException error) { return null; }
    }

    public Statistics summarize(int valueColumn, int labelColumn, NumberStyle style) {
        Statistics stats = new Statistics(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            if (Thread.currentThread().isInterrupted()) return stats;
            BigDecimal value = number(rows.get(i).get(valueColumn), style);
            if (value == null) { stats.skipped++; continue; }
            stats.count++;
            stats.total = stats.total.add(value);
            stats.min = stats.min == null ? value : stats.min.min(value);
            stats.max = stats.max == null ? value : stats.max.max(value);
            if (stats.chart.size() < 12) {
                String label = rows.get(i).get(labelColumn).trim();
                if (label.isEmpty()) label = "Dòng " + (i + 2);
                stats.chart.add(new ChartEntry(label, value.doubleValue()));
            }
        }
        return stats;
    }

    public static final class Statistics {
        public final int rowCount;
        public int count, skipped;
        public BigDecimal total = BigDecimal.ZERO;
        public BigDecimal min, max;
        public final List<ChartEntry> chart = new ArrayList<>();
        Statistics(int rows) { rowCount = rows; }
        public BigDecimal mean() { return count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128); }
    }
}
