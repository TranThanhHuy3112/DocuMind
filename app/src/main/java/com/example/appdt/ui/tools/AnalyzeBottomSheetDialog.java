package com.example.appdt.ui.tools;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.appdt.R;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AnalyzeBottomSheetDialog extends ToolSheet {
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
        String extension = source.getExtension().toLowerCase(Locale.ROOT);
        if (!extension.equals("csv") && !extension.equals("xlsx")) {
            summary.setText(R.string.analysis_excel_unsupported);
            return;
        }
        android.content.ContentResolver resolver = requireContext().getContentResolver();
        executor.execute(() -> {
            List<RevenueCsvParser.Entry> entries = null;
            try (InputStream stream = resolver.openInputStream(Uri.parse(source.getPath()))) {
                if (stream != null) {
                    if (extension.equals("csv")) {
                        entries = RevenueCsvParser.parse(new InputStreamReader(stream,
                                StandardCharsets.UTF_8));
                    } else {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = stream.read(buffer)) != -1) {
                            if (output.size() + count > 10 * 1024 * 1024)
                                throw new IOException("XLSX too large");
                            output.write(buffer, 0, count);
                        }
                        entries = RevenueCsvParser.parse(new StringReader(
                                OfficeTextExtractor.xlsxCsv(output.toByteArray())));
                    }
                }
            } catch (IOException | SecurityException ignored) { }
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
                    NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
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
