package com.example.appdt.ui.tools;

import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.appdt.R;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Collections;

public final class GenerateDraftDialog extends ToolSheet {
    @Override protected ToolKind tool() { return ToolKind.DRAFT; }
    @Override protected int title() { return R.string.draft_title; }
    @Override protected int subtitle() { return R.string.draft_subtitle; }

    @Override protected void render(List<DocumentModel> sources) {
        DocumentModel source = selectedSource(sources);
        addText(binding.toolContent, getString(R.string.tool_source, source.getName()), 13, true);
        addText(binding.toolContent, getString(R.string.ai_send_notice), 13, false);
        MaterialButton outline = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        outline.setText(R.string.draft_outline);
        outline.setFilterTouchesWhenObscured(true);
        MaterialButton report = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        report.setText(R.string.draft_report);
        report.setFilterTouchesWhenObscured(true);
        LinearLayout choices = new LinearLayout(requireContext());
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.addView(outline, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        choices.addView(report, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        binding.toolContent.addView(choices);
        TextView body = addText(binding.toolContent,
                getString(R.string.draft_ready), 15, false);
        body.setId(R.id.draft_result);
        MaterialButton copy = new MaterialButton(requireContext());
        copy.setId(R.id.draft_copy);
        copy.setText(R.string.draft_copy);
        copy.setEnabled(false);
        copy.setFilterTouchesWhenObscured(true);
        Runnable restoreChoices = () -> {
            outline.setEnabled(true);
            report.setEnabled(true);
        };
        outline.setOnClickListener(view -> {
            copy.setEnabled(false);
            report.setEnabled(false);
            askAi(Collections.singletonList(source),
                    "Tạo dàn ý có cấu trúc từ nội dung tệp, nêu các ý chính và dẫn tên nguồn.",
                    body, outline, () -> copy.setEnabled(true), restoreChoices);
        });
        report.setOnClickListener(view -> {
            copy.setEnabled(false);
            outline.setEnabled(false);
            askAi(Collections.singletonList(source),
                    "Viết bản nháp báo cáo ngắn từ nội dung tệp gồm tóm tắt, phân tích, kết luận; "
                            + "giữ nguyên số liệu và chỉ ra phần còn thiếu dữ liệu.", body, report,
                    () -> copy.setEnabled(true), restoreChoices);
        });
        binding.toolActions.addView(copy);
        copy.setOnClickListener(view ->
                copySensitiveText(R.string.draft_title, body.getText(), R.string.draft_copied));
    }
}
