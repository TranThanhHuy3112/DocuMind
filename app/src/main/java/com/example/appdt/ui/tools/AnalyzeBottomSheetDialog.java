package com.example.appdt.ui.tools;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.example.appdt.R;
import com.example.appdt.model.DocumentCategory;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AnalyzeBottomSheetDialog extends ToolSheet {
    private AnalysisViewModel model;
    private String activeSource;
    private LinearLayout tablePanel;
    private TextView status;
    private LinearLayout resultPanel;
    @Override protected ToolKind tool() { return ToolKind.ANALYZE; }
    @Override protected int title() { return R.string.analysis_title; }
    @Override protected int subtitle() { return R.string.analysis_subtitle; }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        model = new ViewModelProvider(this).get(AnalysisViewModel.class);
        super.onViewCreated(view, saved);
        model.state().observe(getViewLifecycleOwner(), this::showState);
        model.statistics().observe(getViewLifecycleOwner(), this::showStatistics);
    }

    @Override protected void render(List<DocumentModel> sources) {
        DocumentModel source = selectedSource(sources);
        activeSource = source.getId();
        addText(binding.toolContent, getString(R.string.tool_source, source.getName()), 13, true);
        boolean pdf = DocumentCategory.canonicalExtension(source).equals("pdf");
        addText(binding.toolContent, getString(pdf ? R.string.analysis_pdf_notice : R.string.analysis_table_notice), 13, false);
        status = addText(binding.toolContent, getString(pdf ? R.string.analysis_pdf_ready : R.string.analysis_loading), 14, false);
        status.setId(R.id.analysis_summary);
        tablePanel = new LinearLayout(requireContext()); tablePanel.setOrientation(LinearLayout.VERTICAL);
        binding.toolContent.addView(tablePanel);
        model.select(source);
        if (pdf) renderPdf(source);
        showState(model.state().getValue());
    }

    private void renderPdf(DocumentModel source) {
        TextInputEditText preview = new TextInputEditText(requireContext());
        preview.setHint(R.string.analysis_pdf_preview);
        preview.setMinLines(3); preview.setMaxLines(7);
        preview.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512 * 1024)});
        preview.setFilterTouchesWhenObscured(true); preview.setSaveEnabled(false);
        preview.setText(model.pdfPreview); binding.toolContent.addView(preview);
        MaterialButton extract = button(getString(R.string.analysis_pdf_extract));
        MaterialButton confirm = button(getString(R.string.analysis_pdf_confirm));
        preview.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                model.editPdfPreview(text.toString());
                if (binding != null) status.setText(R.string.analysis_pdf_review);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        extract.setOnClickListener(view -> {
            model.editPdfPreview(model.pdfPreview);
            extract.setEnabled(false); confirm.setEnabled(false); preview.setEnabled(false); status.setText(R.string.ai_working);
            final String sourceId = activeSource;
            ai.ask(Collections.singletonList(source),
                    "Trích nguyên bảng dữ liệu đầu tiên trong PDF thành CSV, dòng đầu là tên cột. "
                    + "Chỉ trả CSV, không Markdown hay giải thích. Giữ nguyên mọi cột, dấu số, đơn vị và dòng. "
                    + "Ô trống giữ trống, ô có dấu phẩy hoặc xuống dòng đặt trong ngoặc kép. "
                    + "Không tính toán, không tạo thêm dữ liệu. Nếu không có bảng rõ ràng hoặc không thể trích đầy đủ "
                    + "trong giới hạn trả lời thì trả đúng NO_TABLE.", new AiDocumentService.Callback() {
                        @Override public void onSuccess(String answer) {
                            if (binding == null || !sourceId.equals(activeSource)) return;
                            extract.setEnabled(true); confirm.setEnabled(true); preview.setEnabled(true);
                            if (answer.equals("NO_TABLE") || answer.length() > 512 * 1024) { status.setText(R.string.analysis_pdf_no_table); return; }
                            String csv = answer.trim();
                            if (csv.startsWith("```csv\n") && csv.endsWith("```")) csv = csv.substring(7, csv.length() - 3).trim();
                            model.pdfPreview = csv; preview.setText(csv); status.setText(R.string.analysis_pdf_review);
                        }
                        @Override public void onError(String message) {
                            if (binding == null || !sourceId.equals(activeSource)) return;
                            extract.setEnabled(true); confirm.setEnabled(true); preview.setEnabled(true); status.setText(message);
                        }
                    });
        });
        confirm.setOnClickListener(view -> {
            if (preview.getText() == null || preview.getText().toString().trim().isEmpty()) { status.setText(R.string.analysis_pdf_ready); return; }
            model.importPdfTable(preview.getText().toString());
        });
    }

    private void showState(AnalysisViewModel.State state) {
        if (binding == null || tablePanel == null || state == null || !state.sourceId.equals(activeSource)) return;
        tablePanel.removeAllViews();
        resultPanel = null;
        if (state.error != null) { status.setText(state.error); return; }
        if (state.table == null) return;
        TabularData table = state.table;
        status.setText(getString(R.string.analysis_dimensions, table.rows.size(), table.headers.size()));
        if (table.rows.isEmpty()) { addText(tablePanel, getString(R.string.analysis_empty), 14, false); return; }
        if (model.valueColumn < 0) {
            model.valueColumn = table.headers.size() - 1;
            outer: for (int c = table.headers.size() - 1; c >= 0; c--) {
                for (int r = 0; r < Math.min(50, table.rows.size()); r++) {
                    if (TabularData.number(table.rows.get(r).get(c), TabularData.NumberStyle.values()[model.numberStyle]) != null) { model.valueColumn = c; break outer; }
                }
            }
        }
        model.valueColumn = Math.min(model.valueColumn, table.headers.size() - 1);
        model.labelColumn = Math.min(model.labelColumn, table.headers.size() - 1);
        LinearLayout results = new LinearLayout(requireContext()); results.setOrientation(LinearLayout.VERTICAL);
        Spinner values = selector(getString(R.string.analysis_value_column), table.headers, model.valueColumn);
        values.setId(R.id.analysis_value_column);
        Spinner labels = selector(getString(R.string.analysis_label_column), table.headers, model.labelColumn);
        Spinner formats = selector(getString(R.string.analysis_number_style), Arrays.asList(
                "1,234.56 — dấu chấm thập phân", "1.234,56 — dấu phẩy thập phân"), model.numberStyle);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                model.valueColumn = values.getSelectedItemPosition(); model.labelColumn = labels.getSelectedItemPosition();
                model.numberStyle = formats.getSelectedItemPosition(); updateResults(table, results);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        values.setOnItemSelectedListener(listener); labels.setOnItemSelectedListener(listener); formats.setOnItemSelectedListener(listener);
        tablePanel.addView(results); updateResults(table, results);
    }

    private void updateResults(TabularData table, LinearLayout results) {
        if (binding == null) return;
        resultPanel = results;
        model.summarize(table);
        showStatistics(model.statistics().getValue());
    }

    private void showStatistics(TabularData.Statistics stats) {
        if (binding == null || resultPanel == null) return;
        LinearLayout results = resultPanel;
        results.removeAllViews();
        if (stats == null) { addText(results, getString(R.string.analysis_loading), 14, false); return; }
        AnalysisViewModel.State state = model.state().getValue();
        if (state == null || state.table == null || !state.sourceId.equals(activeSource)) return;
        TabularData table = state.table;
        TextView summary = addText(results, "", 15, true);
        if (stats.count == 0) { summary.setText(R.string.analysis_no_numbers); return; }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")); format.setMaximumFractionDigits(6);
        summary.setText(getString(R.string.analysis_statistics, stats.count, stats.rowCount, stats.skipped,
                format.format(stats.total), format.format(stats.mean()), format.format(stats.min), format.format(stats.max)));
        RevenueBarChartView chart = new RevenueBarChartView(requireContext()); chart.setId(R.id.analysis_chart);
        chart.setEntries(stats.chart); results.addView(chart);
        addText(results, getString(R.string.analysis_chart_scope, stats.chart.size(), stats.count), 12, false);
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(8, table.rows.size()); i++) {
            List<String> row = table.rows.get(i);
            preview.append(row.get(model.labelColumn)).append(" → ").append(row.get(model.valueColumn)).append('\n');
        }
        addText(results, getString(R.string.analysis_preview, preview.toString()), 13, false);
    }

    private Spinner selector(String label, List<String> items, int selected) {
        addText(tablePanel, label, 13, true);
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected); spinner.setFilterTouchesWhenObscured(true);
        tablePanel.addView(spinner); return spinner;
    }
    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(requireContext()); button.setText(label);
        button.setFilterTouchesWhenObscured(true); binding.toolActions.addView(button); return button;
    }
    @Override public void onDestroyView() { activeSource = null; status = null; tablePanel = null; resultPanel = null; super.onDestroyView(); }
}
