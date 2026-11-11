package com.example.appdt.ui.tools;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.appdt.R;
import com.example.appdt.model.DocumentCategory;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.utils.DocumentPicker;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AnalyzeBottomSheetDialog extends ToolSheet {
    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String activeSource;

    @Override protected ToolKind tool() { return ToolKind.ANALYZE; }
    @Override protected int title() { return R.string.analysis_title; }
    @Override protected int subtitle() { return R.string.analysis_subtitle; }

    @Override protected void render(List<DocumentModel> sources) {
        DocumentModel source = selectedSource(sources);
        activeSource = source.getId();
        addText(binding.toolContent, getString(R.string.tool_source, source.getName()), 13, true);
        TextView summary = addText(binding.toolContent, getString(R.string.analysis_loading),
                15, false);
        summary.setId(R.id.analysis_summary);
        String extension = DocumentCategory.canonicalExtension(source);
        if (!extension.equals("csv") && !extension.equals("xlsx")) {
            summary.setText(R.string.analysis_excel_unsupported);
            return;
        }
        android.content.Context appContext = requireContext().getApplicationContext();
        android.content.ContentResolver resolver = appContext.getContentResolver();
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            List<RevenueCsvParser.Entry> entries = null;
            Uri uri = Uri.parse(source.getPath());
            if (DocumentPicker.isReadableUri(appContext, uri)) {
                try (InputStream stream = resolver.openInputStream(uri)) {
                    if (stream != null) {
                        byte[] bytes = DocumentPicker.readBoundedBytes(stream, 10 * 1024 * 1024);
                        String csvText = extension.equals("csv")
                                ? new String(bytes, StandardCharsets.UTF_8)
                                : OfficeTextExtractor.xlsxCsv(bytes);
                        entries = RevenueCsvParser.parse(new StringReader(csvText));
                    }
                } catch (IOException | SecurityException ignored) { }
            }
            List<RevenueCsvParser.Entry> result = entries;
            main.post(() -> {
                if (binding == null || !source.getId().equals(activeSource)) return;
                if (result == null) {
                    summary.setText(R.string.analysis_read_error);
                } else if (result.isEmpty()) {
                    summary.setText(R.string.analysis_no_revenue);
                } else {
                    double total = 0;
                    for (RevenueCsvParser.Entry entry : result) total += entry.value;
                    NumberFormat format = NumberFormat.getNumberInstance(VI_LOCALE);
                    format.setMaximumFractionDigits(2);
                    summary.setText(getString(R.string.analysis_summary, result.size(),
                            format.format(total)));
                    RevenueBarChartView chart = new RevenueBarChartView(requireContext());
                    chart.setId(R.id.analysis_chart);
                    chart.setEntries(result);
                    binding.toolContent.addView(chart);
                    addText(binding.toolContent, getString(R.string.analysis_csv_note), 12, false);
                }
            });
        });
    }

    @Override public void onDestroyView() {
        activeSource = null;
        main.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
